package com.balancloud.device.ocpp;

import com.balancloud.device.ocpp.config.OcppProperties;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.service.OcppTransactionStore;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.RawFrame;
import com.balancloud.device.protocol.UplinkEvent;
import com.balancloud.device.protocol.UplinkFrameRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * OCPP 帧的传输层处理：拆 CALL / CALLRESULT / CALLERROR，回执按 OCPP 规格立刻发回。
 *
 * <p>语义解在 {@code Ocpp16JAdapter} 里，这里只管信封与回执。顺序不能反过来：
 * 先把 CALL 交给既有上行路由（归属、去重、解码、落订单），再按事件结果拼回执——
 * StartTransaction 的 transactionId 就是刚在路由里创建的那一行。
 */
@Service
public class OcppFrameService {

    private static final Logger log = LoggerFactory.getLogger(OcppFrameService.class);
    private static final DateTimeFormatter OCPP_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 本批支持的消息；其余只记日志并回空 CALLRESULT，不假装受理业务。 */
    private static final Set<String> SUPPORTED = Set.of(
            "BootNotification", "Heartbeat", "StatusNotification", "Authorize",
            "StartTransaction", "MeterValues", "StopTransaction");

    private final ObjectMapper objectMapper;
    private final OcppSessionRegistry registry;
    private final OcppProperties properties;
    private final UplinkFrameRouter uplinkRouter;
    private final OcppPendingStore pendingStore;
    private final OcppTransactionStore transactionStore;
    private final ApplicationEventPublisher eventPublisher;

    public OcppFrameService(ObjectMapper objectMapper,
                            OcppSessionRegistry registry,
                            OcppProperties properties,
                            UplinkFrameRouter uplinkRouter,
                            OcppPendingStore pendingStore,
                            OcppTransactionStore transactionStore,
                            ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.properties = properties;
        this.uplinkRouter = uplinkRouter;
        this.pendingStore = pendingStore;
        this.transactionStore = transactionStore;
        this.eventPublisher = eventPublisher;
    }

    public void onInbound(String identity, Long tenantId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > properties.getMaxFrameBytes()) {
            log.warn("Dropping oversized OCPP frame from {} ({} chars)", identity, text.length());
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("Invalid OCPP JSON from {}: {}", identity, e.getMessage());
            return;
        }
        if (!root.isArray() || root.size() < 2) {
            log.warn("Invalid OCPP frame from {}: not a message array", identity);
            return;
        }
        int type = root.path(0).asInt(-1);
        String uniqueId = root.path(1).asText("");
        switch (type) {
            case 2 -> handleCall(identity, tenantId, uniqueId, root);
            case 3 -> handleCallResult(identity, tenantId, uniqueId,
                    root.size() > 2 ? root.get(2) : objectMapper.createObjectNode());
            case 4 -> handleCallError(identity, tenantId, uniqueId,
                    root.path(2).asText(""),
                    root.path(3).asText(""));
            default -> log.warn("Unknown OCPP message type {} from {}", type, identity);
        }
    }

    /** 连接关闭：只在注册表确认这条会话已被移除后才会调到这里。 */
    public void onDisconnected(String identity, Long tenantId) {
        if (tenantId == null) {
            return;
        }
        eventPublisher.publishEvent(new UplinkEvent(tenantId,
                new PlatformEvent.Connectivity(identity, false, System.currentTimeMillis())));
    }

    private void handleCall(String identity, Long tenantId, String uniqueId, JsonNode root) {
        String action = root.path(2).asText("");
        JsonNode payload = root.size() > 3 ? root.get(3) : objectMapper.createObjectNode();
        if (!SUPPORTED.contains(action)) {
            // 回 CALLERROR 会让桩按错误策略反复重试；空 CALLRESULT 才是「收到但不处理」。
            log.info("Ignoring unsupported OCPP action {} from {}", action, identity);
            sendCallResult(identity, uniqueId, objectMapper.createObjectNode());
            return;
        }
        RawFrame frame = new RawFrame("ocpp", identity,
                root.toString().getBytes(StandardCharsets.UTF_8),
                Map.of("ocpp.uniqueId", uniqueId, "ocpp.action", action), Instant.now());
        try {
            uplinkRouter.route(identity, action, frame);
        } catch (Exception e) {
            // 业务侧异常不能把整条连接打断：回执仍要发，桩才不会一直重发。
            log.warn("OCPP {} from {} failed in uplink path: {}", action, identity, e.getMessage());
        }
        sendCallResult(identity, uniqueId, confirmation(action, identity, payload));
    }

    private JsonNode confirmation(String action, String identity, JsonNode payload) {
        ObjectNode confirmation = objectMapper.createObjectNode();
        switch (action) {
            case "BootNotification" -> confirmation
                    .put("status", "Accepted")
                    .put("currentTime", OffsetDateTime.now().format(OCPP_TIME))
                    .put("interval", 300);
            case "Heartbeat" -> confirmation.put("currentTime", OffsetDateTime.now().format(OCPP_TIME));
            case "Authorize", "StopTransaction" -> confirmation.set("idTagInfo",
                    objectMapper.createObjectNode().put("status", "Accepted"));
            case "StartTransaction" -> {
                int connectorId = payload.path("connectorId").asInt(1);
                OcppTransaction tx = transactionStore.findActiveAnyTenant(identity, connectorId);
                // 没有平台订单却收到 StartTransaction：不动账，明确回 Invalid，让桩自己停掉。
                if (tx == null) {
                    confirmation.put("transactionId", 0);
                    confirmation.set("idTagInfo", objectMapper.createObjectNode().put("status", "Invalid"));
                } else {
                    confirmation.put("transactionId", tx.getId().intValue());
                    confirmation.set("idTagInfo", objectMapper.createObjectNode().put("status", "Accepted"));
                }
            }
            default -> {
                // StatusNotification / MeterValues 的回执是空对象。
            }
        }
        return confirmation;
    }

    private void handleCallResult(String identity, Long tenantId, String uniqueId, JsonNode payload) {
        OcppPendingStore.PendingCommand pending = pendingStore.take(identity, uniqueId);
        if (pending == null) {
            log.debug("Unmatched OCPP CALLRESULT {} from {}", uniqueId, identity);
            return;
        }
        String raw = payload == null ? "" : payload.path("status").asText("");
        // RemoteStart/RemoteStop 回 Accepted，UnlockConnector 回 Unlocked；其余状态都按失败处理。
        boolean success = raw.isBlank() || "Accepted".equalsIgnoreCase(raw) || "Unlocked".equalsIgnoreCase(raw);
        publishAck(identity, tenantId, pending, success, raw, "");
    }

    private void handleCallError(String identity, Long tenantId, String uniqueId,
                                 String errorCode, String errorDescription) {
        OcppPendingStore.PendingCommand pending = pendingStore.take(identity, uniqueId);
        if (pending == null) {
            log.debug("Unmatched OCPP CALLERROR {} from {}", uniqueId, identity);
            return;
        }
        publishAck(identity, tenantId, pending, false, errorCode, errorDescription);
    }

    private void publishAck(String identity, Long tenantId, OcppPendingStore.PendingCommand pending,
                            boolean success, String rawResult, String message) {
        if (tenantId == null) {
            log.warn("Cannot attribute OCPP command ack {} from {}", pending.cmdId(), identity);
            return;
        }
        eventPublisher.publishEvent(new UplinkEvent(tenantId, new PlatformEvent.CommandAck(
                identity, pending.cmdId(), pending.portNo(), pending.type(), success,
                rawResult, message, System.currentTimeMillis())));
    }

    private void sendCallResult(String identity, String uniqueId, JsonNode payload) {
        ArrayNode array = objectMapper.createArrayNode();
        array.add(3).add(uniqueId).add(payload);
        registry.send(identity, array.toString());
    }
}

package com.balancloud.mqtt;

import com.balancloud.device.protocol.RawFrame;
import com.balancloud.device.protocol.UplinkFrameRouter;
import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.mqtt.config.MqttProperties;
import com.balancloud.mqtt.event.DownlinkCommandEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MqttGateway implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);
    private static final String UPLINK_FILTER = "balancloud/up/+/#";
    private static final String UPLINK_PREFIX = "balancloud/up/";
    private static final String DOWNLINK_PREFIX = "balancloud/down/";
    private static final String DOWNLINK_FILTER = "balancloud/down/+/cmd";

    private final MqttProperties properties;
    private final MqttLogService logService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UplinkFrameRouter uplinkFrameRouter;
    private final ClusterNode clusterNode;
    private final ExecutorService connector = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-connector");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile MqttClient client;
    private volatile LocalDateTime lastConnectedAt;
    private volatile boolean sharedSubscription;

    public MqttGateway(MqttProperties properties,
                       MqttLogService logService,
                       ObjectMapper objectMapper,
                       ApplicationEventPublisher eventPublisher,
                       UplinkFrameRouter uplinkFrameRouter,
                       ClusterNode clusterNode) {
        this.properties = properties;
        this.logService = logService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.uplinkFrameRouter = uplinkFrameRouter;
        this.clusterNode = clusterNode;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            log.info("MQTT gateway disabled");
            return;
        }
        connectAsync();
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 15_000)
    public void ensureConnected() {
        if (!properties.isEnabled()) {
            return;
        }
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            connectAsync();
        }
    }

    private void connectAsync() {
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        connector.submit(() -> {
            try {
                connect();
            } finally {
                connecting.set(false);
            }
        });
    }

    private void connect() {
        try {
            MqttClient current = client;
            if (current == null) {
                current = new MqttClient(properties.getBrokerUrl(), instanceClientId(), new MemoryPersistence());
                current.setCallback(new GatewayCallback());
                client = current;
            }
            if (current.isConnected()) {
                return;
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setMaxInflight(200);
            if (StringUtils.hasText(properties.getUsername())) {
                options.setUserName(properties.getUsername());
                options.setPassword(properties.getPassword() == null
                        ? new char[0] : properties.getPassword().toCharArray());
            }
            current.connect(options);
        } catch (Exception e) {
            log.warn("MQTT connect to {} failed: {}", properties.getBrokerUrl(), e.getMessage());
        }
    }

    public boolean publishUplink(String deviceNo, String event, Object payload) {
        return publish(UPLINK_PREFIX + deviceNo + "/" + event, deviceNo, payload);
    }

    /**
     * 下行原始帧：主题与正文都由适配器决定，本网关只负责发出去并记录 OUT 日志。
     * 非 mqtt 通道的帧直接拒绝——这里是 MQTT 传输，不能替别的传输代发。
     */
    public boolean publishRaw(RawFrame frame) {
        if (frame == null || !StringUtils.hasText(frame.topicOrPeer())) {
            return false;
        }
        if (!"mqtt".equals(frame.channel())) {
            log.warn("MqttGateway cannot send {} frame to {}", frame.channel(), frame.topicOrPeer());
            return false;
        }
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            return false;
        }
        String topic = frame.topicOrPeer();
        try {
            MqttMessage message = new MqttMessage(frame.body());
            message.setQos(properties.getQos());
            current.publish(topic, message);
            if (topic.startsWith(DOWNLINK_PREFIX)) {
                String[] parts = topic.split("/");
                logService.record("OUT", topic, parts.length > 2 ? parts[2] : "", frame.bodyAsText());
            }
            return true;
        } catch (Exception e) {
            log.warn("MQTT publish to {} failed: {}", topic, e.getMessage());
            return false;
        }
    }

    private boolean publish(String topic, String deviceNo, Object payload) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            return false;
        }
        try {
            String json = payload instanceof String stringPayload
                    ? stringPayload
                    : objectMapper.writeValueAsString(payload);
            MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            current.publish(topic, message);
            if (topic.startsWith(DOWNLINK_PREFIX)) {
                // 模拟器发出的上行会被本网关再接收并记录为 IN，避免同一报文重复展示
                logService.record("OUT", topic, deviceNo, json);
            }
            return true;
        } catch (Exception e) {
            log.warn("MQTT publish to {} failed: {}", topic, e.getMessage());
            return false;
        }
    }

    public boolean isConnected() {
        MqttClient current = client;
        return current != null && current.isConnected();
    }

    /** 客户端 ID 必须每实例唯一，否则 broker 会把先连上的节点顶下线。 */
    public String instanceClientId() {
        return properties.getClientId() + "-" + clusterNode.id();
    }

    public boolean isSharedSubscription() {
        return sharedSubscription;
    }

    public String getSubscriptionGroup() {
        return properties.getSharedGroup();
    }

    public String getBrokerUrl() {
        return properties.getBrokerUrl();
    }

    public LocalDateTime getLastConnectedAt() {
        return lastConnectedAt;
    }

    @PreDestroy
    public void shutdown() {
        connector.shutdownNow();
        MqttClient current = client;
        if (current != null) {
            try {
                if (current.isConnected()) {
                    current.disconnect(1000);
                }
                current.close();
            } catch (Exception e) {
                log.debug("Error while closing MQTT client: {}", e.getMessage());
            }
        }
    }

    private final class GatewayCallback implements MqttCallbackExtended {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            lastConnectedAt = LocalDateTime.now();
            log.info("MQTT {} to {} as {}", reconnect ? "reconnected" : "connected", serverURI, instanceClientId());
            MqttClient current = client;
            int qos = properties.getQos();
            String group = properties.getSharedGroup();
            try {
                if (StringUtils.hasText(group)) {
                    // 上行按共享订阅分发，一份报文只进一个实例；下行是仿真器扮演设备的指令通道，
                    // 每个实例都要收到，再交给扮演设备的 Leader 实例应答，否则同一指令会落到随机节点。
                    current.subscribe(new String[]{shared(group, UPLINK_FILTER), DOWNLINK_FILTER},
                            new int[]{qos, qos});
                    sharedSubscription = true;
                    log.info("Subscribed to shared group {} on {}, plain {}", group, UPLINK_FILTER, DOWNLINK_FILTER);
                    return;
                }
                subscribePlain(current, qos);
            } catch (Exception e) {
                if (!StringUtils.hasText(group)) {
                    log.error("Failed to subscribe to MQTT topics: {}", e.getMessage());
                    return;
                }
                log.warn("Shared subscription in group {} failed: {}; fallback to plain subscription",
                        group, e.getMessage());
                try {
                    subscribePlain(current, qos);
                    sharedSubscription = false;
                } catch (Exception fallbackError) {
                    log.error("Failed to subscribe to MQTT topics: {}", fallbackError.getMessage());
                }
            }
        }

        private void subscribePlain(MqttClient current, int qos) throws Exception {
            current.subscribe(new String[]{UPLINK_FILTER, DOWNLINK_FILTER}, new int[]{qos, qos});
            log.info("Subscribed to {} and {}", UPLINK_FILTER, DOWNLINK_FILTER);
        }

        private String shared(String group, String filter) {
            return "$share/" + group + "/" + filter;
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            byte[] body = message.getPayload();
            String[] parts = topic.split("/");
            String deviceNo = parts.length > 2 ? parts[2] : "";
            if (topic.startsWith(DOWNLINK_PREFIX)) {
                // 下行普通订阅，每个实例都会收到；此处不做幂等，否则先收到报文的后排实例会把
                // Leader 实例的那份吞掉，指令反而没人应答。QoS1 重投由指令状态机兜住。
                eventPublisher.publishEvent(new DownlinkCommandEvent(deviceNo,
                        new String(body, StandardCharsets.UTF_8)));
                return;
            }
            // 主题切分是 MQTT 传输的事，留在网关；之后的链路只认设备号与原始帧。
            String event = parts.length > 3 ? parts[3] : "";
            uplinkFrameRouter.route(deviceNo, event,
                    new RawFrame("mqtt", topic, body, Map.of(), Instant.now()));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}

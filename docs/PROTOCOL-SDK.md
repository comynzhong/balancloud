# BALANCLOUD 设备协议与场景扩展 SDK

> **状态（2026-10-28）**：接口、注册表与链路**均已接线**（§46 Step 1–11）；L0 也不再只有 MQTT 一条——
> §48 落了 OCPP 1.6J 的 WebSocket 服务端与 `OCPP16J` adapter，§49 补了四轮交流 `AC_CAR` 场景与即插即充。
> 在册协议 `BALAN_MQTT` / `OCPP16J`，在册场景 `TWOWHEEL`（默认）/ `DC_CAR` / `AC_CAR`。
>
> - **上行**：`MqttGateway` 与 `device/ocpp/OcppFrameService` 把各自的帧交给同一个 `UplinkFrameRouter`：
>   记 IN 日志 → 反查租户与 `charger.protocol` → 幂等 → `registry.require(protocol).decode(frame)` →
>   发布 `UplinkEvent`。`DeviceMessageHandler` 里按事件名 `switch (event)` 的解码已删除；解不出的帧落
>   `Unrecognized`，仍按原样重投 `UplinkMessageEvent`（平台侧 OTA 进度还在用原始帧）。
> - **下行**：`CommandService` 与 OTA 都通过 `adapter.encode` 出帧，平台不再拼报文；出来的帧由
>   `DownlinkDispatcher` 按 `RawFrame.channel()` 选传输，`ocpp` 那一路走 Redis 跨实例转发。
> - **场景**：`charger.scene` 已随 V27 落库（默认 `TWOWHEEL`），OCPP 交易映射在 V29，四轮交流与
>   即插即充在 V30；`ChargingService` 的准入与自动停、`OccupancyService` 的占位判定、停机超时统一走
>   `OrderFlowRegistry.require(scene)`。
> - **观测**：`GET /api/v1/device/protocols`、`GET /api/v1/device/flows`、
>   `GET /api/v1/chargers/scenes`（见第 9 节）。
> - **门禁**：主仓 `mvn -B test` **577 条全绿**（`balan-common` 7 + `balan-core` 370 +
>   `balan-platform` 200，JDK 17）；两条反证（`TEST_PROTOCOL` / `TEST_SCENE`）只在测试作用域。
>
> **仍然成立的一条边界**：`dedupKey` 这个接口已经定义、`Ocpp16JAdapter` 也实现了它，但
> `UplinkFrameRouter` 的幂等仍按正文指纹（`MqttDedupService.firstSeen`），**路由没有消费 `dedupKey`**——
> 接二进制或带序号的方言时要补这一步。另外纯 TCP 的私有帧定界仍未做。

## 1. 这套抽象解决什么

现状（改之前）：接一个新厂商的桩要改接入层。主题切分写死在 `MqttGateway`，事件名是字符串
`switch`，报文字段名直接出现在业务代码里，`charger.protocol` 这一列唯一的用处是让
`ChargingService` 拒绝非 `BALAN_MQTT` 的设备。

改之后的分工：

| 层 | 内容 | 现状 |
| --- | --- | --- |
| **L0 传输** | MQTT 客户端、Netty TCP 帧定界、OCPP WebSocket 会话、TLS/证书、重连与在途队列 | MQTT 与 OCPP 1.6J WebSocket **在册**（`MqttGateway`、`device/ocpp/`）；纯 TCP 帧定界与 TLS/证书**未做** |
| **L1 语义** | 报文 ↔ 平台事件的编解码、能力声明、幂等键 | **做**（`BALAN_MQTT`、`OCPP16J` 两个 adapter） |
| **L2 注册与观测** | 注册表、重名校验、每个协议的在册设备数 | **做**（热加载不做） |

**必须先说清的一条边界**：OCPP 1.6J 已经在册，但**只会讲纯 TCP 私有帧的桩仍然要补一次 L0**。
L1 只保证「一旦有帧进来，业务代码不用改」。真正接一种新协议的成本 =
一次 L0（传输不是 MQTT/OCPP-WS 时）+ 一个 adapter 类 + 一致性测试通过。

## 2. 协议适配器接口

`com.balancloud.device.protocol.DeviceProtocolAdapter`（AGPL 模块内，第三方 adapter 可闭源分发，
只要不修改 core；理由见 MVP 文档 §42.8 第 1 条）。

```java
public interface DeviceProtocolAdapter {
    /** 与 charger.protocol 一一对应，全集群唯一；如 BALAN_MQTT。 */
    String protocol();

    ProtocolCapabilities capabilities();

    /** 一条传输帧 → 0..n 个平台事件；解不出来返回空集合，不抛业务异常。 */
    List<PlatformEvent> decode(RawFrame frame);

    /** 平台指令 → 传输帧；主题与正文由适配器决定，平台不拼字符串。 */
    RawFrame encode(PlatformCommand command);

    /** 幂等键：二进制或带时间戳的方言不能靠正文哈希，交由适配器给；缺省回落到正文指纹。 */
    default Optional<String> dedupKey(RawFrame frame) {
        return Optional.empty();
    }
}
```

三条硬规矩，写代码时就按它来，别等评审：

1. `decode` **不许抛业务异常**。解不出来就返回空集合或一个 `Unrecognized`；
   抛异常会让一条坏报文把整条上行链路带停。
2. `encode` 出来的 `cmdId` 必须能和设备回的 `cmd_ack` 对上，否则指令状态机只能靠超时判失败。
3. adapter 里**不查库、不写库、不发事件**。它只做字节 ↔ 语义的翻译，租户归属和落库都在 router 之后。

## 3. 值类型

### 3.1 一帧原始报文

```java
public record RawFrame(String channel, String topicOrPeer, byte[] body,
                       Map<String, String> headers, Instant receivedAt) {
    public String bodyAsText();
    public static RawFrame mqtt(String topic, String payload);
}
```

`channel` 目前有 `"mqtt"` 与 `"ocpp"` 两种：前者由 `MqttGateway` 造帧，后者由
`device/ocpp/OcppFrameService` 造帧，`Ocpp16JAdapter` 的 `encode` 也回 `ocpp`。下行侧
`DownlinkDispatcher` 就是按这个字段选传输的，所以**接一种新传输 = 新增一个 channel 值 +
在 dispatcher 里加一路**，`decode` 之上完全不用改。纯 TCP 等传输仍留位。

### 3.2 平台只认这六种上行事件

```java
public sealed interface PlatformEvent {
    record Connectivity(String deviceNo, boolean online, long ts) implements PlatformEvent {}
    record MetricReport(String deviceNo, long ts, List<PortSample> ports) implements PlatformEvent {}
    record VendorAlert(String deviceNo, Integer portNo, AlertHint hint, String rawType,
                       String message, Double value) implements PlatformEvent {}
    record CommandAck(String deviceNo, String cmdId, Integer portNo, String type, boolean success,
                      String rawResult, String message, long ts) implements PlatformEvent {}
    record OrderLifecycle(String deviceNo, Integer portNo, String orderNo,
                          String stopReason, Double energyTotal, long ts) implements PlatformEvent {}
    /** 解不出归属或语义的帧：带原因进日志，不当成「没有这条报文」。 */
    record Unrecognized(String deviceNo, String reason) implements PlatformEvent {}
}
```

`sealed` 是刻意的：厂商事件名拼错，原来会被 `switch` 的 default 分支静默丢弃；
以后要么归进 `Unrecognized`（能看见），要么编译期就要求补一个分支。

`AlertHint` 带 `recognized` 标志和厂商原文（`vendorType` / `alertTypeName`），
告警类型映射不到时不会再把级别偷偷降级，而是把原文留在记录里。
`CommandAck.rawResult` 同理：厂商回执的原文（内置协议是 `SUCCESS` / `FAILED`）要留着，
失败文案由平台拼「设备执行失败: 原文」，适配器里不塞平台的中文模板。

### 3.3 一个端口的一次采样

```java
public record PortSample(Integer portNo, String status, Double voltage, Double currentA,
                         Double temperature, Double powerKw, Double energyTotal,
                         Integer signalStrength, Boolean plugged,
                         Integer soc, Double bmsDemandVoltage, Double bmsDemandCurrent,
                         List<Double> gunTemperatures, Integer gunNo, Integer masterGunNo,
                         Long chargeSeconds, String stopReasonCode, String meterSerial) {
    public static PortSample of(/* 前九个字段 */);
}
```

前九个字段 = 现有内置 MQTT 协议的全部信号，`of(...)` 就是给现有协议用的。
后面九个是 3/4 轮与大型车要用的，**全部可空**：两轮插座解不出来就留 null，
adapter 不允许替设备造默认值（造出来的 SOC 会进账单）。

`Boolean plugged` 用包装类型而不是 `boolean`：设备没说 ≠ 没插枪，占位费要区分这两种。

### 3.4 能力声明

```java
public record ProtocolCapabilities(boolean remoteStart, boolean remoteStop, boolean unlock,
                                   boolean setThreshold, boolean ota,
                                   boolean perPortMetering, boolean bmsHandshake,
                                   int maxPorts, PortIdModel portIdModel) {
    public enum PortIdModel { PORT_NUMBER, GUN_NUMBER, GUN_PLUS_PLUG }
}
```

业务代码判断「这台桩能不能远程启动 / 有没有每路电表 / 支不支持 OTA」只读这里，
**不读 `protocol` 字符串，也不读 `scene`**。

`PortIdModel` 是四类桩最结构性的一处差异：两轮是端口号，四轮交流是枪号，
直流一枪两孔还要加插孔号；平台侧的端口模型要按它来解释 `portNo`。

### 3.5 平台想对设备做的一件事

```java
public record PlatformCommand(String action, String cmdId, String deviceNo, Integer portNo,
                              CommandType type, Map<String, Object> params, long ts) {
    public static final String ACTION_CMD = "cmd";
    public static final String ACTION_OTA = "ota";
    public static PlatformCommand control(String cmdId, String deviceNo, Integer portNo,
                                          CommandType type, Map<String, Object> params);
    public static PlatformCommand ota(String deviceNo, Map<String, Object> fields);
}
```

`action` 决定映射成哪个下行事件段。指令有 `cmdId/portNo/type` 信封，OTA 是扁平字段，
两者正文形状不同但共用一条通道，所以放同一个 record。

## 4. 注册表

```java
public class ProtocolAdapterRegistry {
    public ProtocolAdapterRegistry(List<DeviceProtocolAdapter> adapters) { /* 重名即抛异常 */ }
    public DeviceProtocolAdapter require(String protocol);   // 未注册 → 明确异常，不回落默认
    public Optional<DeviceProtocolAdapter> find(String protocol);
    public Set<String> available();
}
```

两条设计决定，都是为了避免最难查的那类故障：

- **重名即启动失败**，而不是后注册的覆盖先注册的。覆盖的症状是「设备时好时坏」，
  而且只在多实例部署时出现。
- **`require` 未注册直接抛异常**，绝不回落 `BALAN_MQTT`。回落等于「一个云快充桩被当成
  内置协议发指令」，症状是设备什么都收不到。

`BALAN_MQTT` 的实现由 `ProtocolAutoConfiguration` 以 `@AutoConfiguration` + 独立 `@Bean` 装配
（和 `SpiDefaultsAutoConfiguration` 那套同一个思路）。注册表本身也是 bean：构造注入
`List<DeviceProtocolAdapter>`，启动时建索引。

> 适配器 bean **没有**加 `@ConditionalOnMissingBean`：协议是叠加的。标准版要加一个增强实现时，
> 要么用同名 bean 覆盖内置那个（Spring 的 bean 覆盖规则），要么新起一个协议名；两个不同名实现
> 抢同一个 `protocol()` 会在启动期直接失败。

## 5. 场景与订单流程

一轮改动同时要把「订单流程按场景分叉」的缝定下来，接口在 `com.balancloud.order.flow`。

```java
public interface OrderFlow {
    String scene();
    void assertStartable(StartContext ctx);
    List<PreChargePhase> preChargePhases();
    boolean startsOnCommandAck();
    OccupyDecision resolveOccupy(OccupyContext ctx);
    List<AutoStopRule> autoStopRules(RateSnapshot snapshot);
    Duration stoppingTimeout();
}
```

配套的五个类型：

| 类型 | 内容 |
| --- | --- |
| `PreChargePhase` | `PLUGGED / AUTHENTICATING / HANDSHAKING / BMS_NEGOTIATING / PRE_CHARGE` |
| `StartContext` | 设备在线状态、端口状态、`plugged`、当前订单号、是否有进行中订单、`ProtocolCapabilities` |
| `OccupyContext` | 占位开关、下单用户、端口是否存在、端口状态、报文直给的 `plugged` |
| `OccupyDecision` | `portStatus` + `settleFirst`；构造器只接受 `IDLE / OCCUPIED / FAULT` |
| `AutoStopRule` | `name()` + `evaluate(ChargeOrder, RateSnapshot) → Optional<Hit>`，`Hit(StopReason, detail)` |

### 5.1 订单状态机不按场景改

`OrderStatus` 六个状态（`PENDING_START / CHARGING / STOPPING / COMPLETED / FAILED / ABNORMAL`）
表达的是**钱和责任的阶段**，不是电气的阶段。所有场景共用，不加场景专属状态。

场景真正不一样的三处，正好落在上面三个方法里：CHARGING **之前**要经过哪些阶段
（`preChargePhases`）、ack 之后算不算开始充（`startsOnCommandAck`）、结束之后怎么判占位
（`resolveOccupy`）。

### 5.2 握手半状态只进设备影子

`PLUGGED → HANDSHAKING` 这类「桩还没供电但已经在准备」的半状态**只写 `DeviceShadowService` 的端口影子**，
`charger_port.status` 的词汇表固定四个值不扩。`OccupyDecision` 的构造器就是这条约束的代码形态。

三条理由都对着现有代码，完整版见 MVP 文档 §45.5，这里只留结论：
扩了要同时改三处判定（`ChargingService` 的准入、抢端口的条件更新、影子归一写库），
漏任何一处的症状都是「端口卡在握手态再也启不动」；而握手阶段电流为零，
现有「靠电流猜状态」的归一逻辑会把设备直接猜回 IDLE，制造状态抖动。

### 5.3 四条红线

1. `scene` 只允许出现在三处：默认值装配、建档校验/下拉、展示裁剪。
2. 业务判断看 `ProtocolCapabilities`，不看 `scene`。同一场景可以接不同厂商的桩。
3. 每张新表声明归属 `common / twowheel / car / heavy`，跨侧只能进 core。
4. 只对单个场景有意义的字段放扩展表，`charge_order` 不加列。

`if ("AC_CAR".equals(scene))` 这类分支命中文件数超过 8 个，就升级为独立产品线模块
（复用 core / platform 已经验证过的分层机制），不继续加 if。

### 5.4 装配纪律

和协议注册表完全同构：`OrderFlowRegistry` 构造注入 `List<OrderFlow>` 按 `scene()` 建索引，
**同一场景出现两个实现即启动失败**。默认实现 `TwoWheelOrderFlow` 放 core，`@ConditionalOnMissingBean`。
scene 为空回落 `TWOWHEEL`（V27 之前的存量行没有这一列）。

## 6. 一致性测试

第三方 adapter 只要继承基类就能自证接得对（基类随 §46 Step 3 落地，5 条断言）：

1. 坏帧返回空集合，不抛异常；
2. `encode` 出的 `cmdId` 能对回 `cmd_ack`；
3. 能力声明与实际行为一致（声明 `remoteStart` 就必须真能 `encode` 出启动指令）；
4. `dedupKey` 对同一帧重投稳定；
5. `Unrecognized` 无副作用（不落库、不改订单）。

还会加一条借内置仿真器当对端的测试：adapter `encode` 出来的下行帧喂给仿真器，
仿真器回的 `cmd_ack` 能被同一个 adapter `decode` 解析。
这条卡住的是「把平台自己的方言当成协议规范写死」。

## 7. 怎么写一个 adapter

三步：**实现类 → 注册 bean → 继承一致性测试**。下面这个 `VENDOR_X` 是能跑的完整例子
（MQTT 上行 JSON，主题 `vendorx/up/{设备号}/{事件}`，下行 `vendorx/down/{设备号}`）。

### 7.1 实现类

```java
package com.example.vendorx;

import com.balancloud.device.protocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class VendorXAdapter implements DeviceProtocolAdapter {

    public static final String PROTOCOL = "VENDOR_X";

    /** 只会远程启动、每路有电表；没有停止/解锁/OTA/BMS，最多 2 路，端口号就是插座号。 */
    private static final ProtocolCapabilities CAPABILITIES = new ProtocolCapabilities(
            true, false, false, false, false,
            true, false, 2, ProtocolCapabilities.PortIdModel.PORT_NUMBER);

    private final ObjectMapper objectMapper;

    public VendorXAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public ProtocolCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<PlatformEvent> decode(RawFrame frame) {
        String topic = frame.topicOrPeer();
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (topic == null || !topic.startsWith("vendorx/up/") || parts.length < 4) {
            return List.of(new PlatformEvent.Unrecognized("", "上行主题不是 vendorx/up/{deviceNo}/{event}"));
        }
        String deviceNo = parts[2];
        JsonNode root;
        try {
            root = objectMapper.readTree(frame.bodyAsText());
        } catch (Exception e) {
            return List.of();                 // 坏帧只丢这一帧，不抛异常
        }
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return switch (parts[3]) {
            case "data" -> List.of(new PlatformEvent.MetricReport(deviceNo, ts,
                    List.of(PortSample.of(root.path("port").asInt(1), root.path("state").asText(null),
                            num(root, "u"), num(root, "i"), num(root, "t"), num(root, "p"),
                            num(root, "e"), intOrNull(root, "rssi"), bool(root, "plugged")))));
            case "ack" -> List.of(new PlatformEvent.CommandAck(deviceNo,
                    root.path("id").asText(null),
                    intOrNull(root, "port"),
                    root.path("cmd").asText(null),
                    "OK".equalsIgnoreCase(root.path("result").asText()),   // 厂商词表 → boolean
                    root.path("result").asText(),                          // 原文留着，别翻译
                    root.path("msg").asText(""), ts));
            default -> List.of(new PlatformEvent.Unrecognized(deviceNo, "未知事件 " + parts[3]));
        };
    }

    @Override
    public RawFrame encode(PlatformCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", command.cmdId());       // 回执里的 id 必须与这里一致，否则只能靠超时判失败
        payload.put("port", command.portNo());
        payload.put("cmd", command.type() == null ? null : command.type().name());
        payload.putAll(command.params());
        String topic = "vendorx/down/" + command.deviceNo();
        return RawFrame.mqtt(topic, json(payload));
    }

    /** 设备没说 ≠ 0：缺字段一律给 null，适配器不替设备造默认值（造出来的 SOC/电量会进账单）。 */
    private static Double num(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asDouble() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asInt() : null;
    }

    private static Boolean bool(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asBoolean() : null;
    }

    private String json(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

几个容易踩的点：

- `capabilities()` 要**诚实**。声明 `remoteStart=true`，启动接口就会放行这台桩；声明
  `perPortMetering=true`，计费就按每路电量算。声明了却做不到，症状是「下单成功但设备不动」。
- `capabilities().ota()=false` 时不用处理 `PlatformCommand.ACTION_OTA`；一旦改成 `true`，
  `encode` 必须能出 OTA 帧，否则一致性测试第 3 条会红。
- `CommandAck.rawResult` 保留厂商原文。平台的失败文案（「设备执行失败: 原文」）在订单侧拼。
- `decode` 不许有状态：同一个坏帧连续解两次，结果必须完全一样（一致性测试第 5 条就查这个）。

### 7.2 注册 bean

```java
@AutoConfiguration
public class VendorXAutoConfiguration {

    @Bean
    public VendorXAdapter vendorXAdapter(ObjectMapper objectMapper) {
        return new VendorXAdapter(objectMapper);
    }
}
```

再把它写进 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
`ProtocolAutoConfiguration` 那条 `protocolAdapterRegistry(List<DeviceProtocolAdapter>)` 会自动把它收进注册表，
不需要改 core。

- **不要**加 `@ConditionalOnMissingBean`：协议是叠加的。要替换内置实现就用同名 bean 覆盖。
- 两个不同名的 bean 抢同一个 `protocol()` → 启动期直接抛「协议重复注册: XXX」，不是后注册顶掉先注册。
- 注册完，把 `charger.protocol` 填成 `VENDOR_X`，上行才会被这个 adapter 解；`charger.protocol`
  仍是唯一的选择键。
- **漏注册的症状**：上行只记 IN 日志、帧被丢弃（日志里有「协议未注册」），启动接口返回
  「当前设备协议暂不支持远程启动」。先查 `GET /api/v1/device/protocols` 再查报文。

### 7.3 一致性测试

```java
class VendorXAdapterTest extends ProtocolAdapterConformanceTest {

    private final VendorXAdapter adapter = new VendorXAdapter(new ObjectMapper());

    @Override protected DeviceProtocolAdapter adapter() { return adapter; }

    @Override protected RawFrame metricFrame() {
        return RawFrame.mqtt("vendorx/up/VX-001/data",
                "{\"port\":1,\"state\":\"CHARGE\",\"u\":220.1,\"i\":5.2,\"p\":1.14,\"ts\":1700000000000}");
    }

    @Override protected RawFrame ackFrame(String cmdId, boolean success) {
        return RawFrame.mqtt("vendorx/up/VX-001/ack",
                "{\"id\":\"" + cmdId + "\",\"result\":\"" + (success ? "OK" : "ERR") + "\"}");
    }

    @Override protected PlatformCommand startCommand(String cmdId) {
        return PlatformCommand.control(cmdId, "VX-001", 1, CommandType.START, Map.of());
    }

    @Override protected RawFrame malformedMetricFrame() {
        return RawFrame.mqtt("vendorx/up/VX-001/data", "not-json");
    }

    @Override protected String adapterMetricTopicPrefix() { return "vendorx/up/VX-001/"; }
}
```

---

## 8. 怎么加一个场景

场景决定的是**订单时序**，不是协议能力。加一个 `AC_CAR` 的步骤：

### 8.1 实现 `OrderFlow`

```java
public class AcCarOrderFlow implements OrderFlow {

    @Override public String scene() { return "AC_CAR"; }

    @Override public void assertStartable(StartContext ctx) {
        if (!"1".equals(ctx.plugged())) {
            throw new ServiceException(400, "该场景必须插枪后才能启动");
        }
        if (!ctx.idle() && !ctx.occupied()) {
            throw new ServiceException(400, "端口当前不是空闲状态，无法启动充电");
        }
    }

    /** 枪插上之后还要经过鉴权与 BMS 协商；两轮返回空列表。只写设备影子，不动端口状态。 */
    @Override public List<PreChargePhase> preChargePhases() {
        return List.of(PreChargePhase.AUTHENTICATING, PreChargePhase.BMS_NEGOTIATING);
    }

    /** 车桩的 START ack 只代表握手成功，真正开始充电要等设备上报。 */
    @Override public boolean startsOnCommandAck() { return false; }

    @Override public OccupyDecision resolveOccupy(OccupyContext ctx) {
        if (!ctx.portExists()) return OccupyDecision.releaseToIdle();
        if (OccupyDecision.FAULT.equals(ctx.portStatus())) return OccupyDecision.fault();
        return ctx.pluggedIn() ? OccupyDecision.keepOccupied() : OccupyDecision.releaseToIdleAfterSettle();
    }

    /** 车类多一条「目标 SOC」；命中顺序即列表顺序。`AcCarRules` 是这里的常量类，
     *  写法照 TwoWheelOrderFlow 里那两个 AutoStopRule 的匿名实现。 */
    @Override public List<AutoStopRule> autoStopRules(RateSnapshot snapshot) {
        return List.of(AcCarRules.AMOUNT_LIMIT, AcCarRules.TARGET_SOC, AcCarRules.TIME_LIMIT);
    }

    /** 车桩 STOPPING 到断电可能更慢，超时从 15 秒放宽到 45 秒。 */
    @Override public Duration stoppingTimeout() { return Duration.ofSeconds(45); }
}
```

七个方法各自的业务含义：

| 方法 | 回答什么问题 | 改错的症状 |
| --- | --- | --- |
| `scene()` | 这个流程归哪一类桩 | 与另一个实现重名 → 启动期「场景流程重复注册」 |
| `assertStartable` | 下单前必须满足什么 | 漏判 → 起了不该起的单；多判 → 合法请求被 400 |
| `preChargePhases` | CHARGING 之前要经过哪些阶段 | 只影响影子与展示，不影响订单状态 |
| `startsOnCommandAck` | ack 之后算不算开始充 | 车桩上写 `true` → 订单提前进入 CHARGING，电量为 0 却开始计时 |
| `resolveOccupy` | 结束/拔枪后端口落在哪 | 写错 → 端口卡在 OCCUPIED 或占位费漏收 |
| `autoStopRules` | 什么时候自动停 | 顺序错了 → 命中原因与改造前不一致 |
| `stoppingTimeout` | STOPPING 多久判失败 | 太短 → 车桩还在断电就判超时；太长 → 端口长时间卡住 |

### 8.2 注册 bean

```java
@AutoConfiguration
public class AcCarFlowAutoConfiguration {

    @Bean
    public AcCarOrderFlow acCarOrderFlow() {
        return new AcCarOrderFlow();
    }
}
```

同样写进 `AutoConfiguration.imports`。`OrderFlowRegistry` 按 `scene()` 建索引
（`trim().toUpperCase()` 归一），**同一场景两个实现即启动失败**；`require(null/空)` 回落
`TWOWHEEL`（V27 之前的存量行）。

> 给两轮做替换实现时用**同名 bean** 覆盖 `twoWheelOrderFlow`，不要按类型加
> `@ConditionalOnMissingBean(OrderFlow.class)`——那样任何新场景都会顺带把两轮默认实现挤掉。

### 8.3 建档与前端

注册完不需要改校验代码，`ChargerService.normalizeScene` 的合法集合就是注册表：

- 新建不带 `scene` → 落 `TWOWHEEL`；传了未注册值 → 400「场景不合法: X，已注册场景 [...]」。
- 编辑时不传 `scene` → 保持原值（避免旧客户端把车桩静默改回两轮）。
- 前端下拉来自 `GET /api/v1/chargers/scenes`，中文名在
  `frontend/src/utils/balan.js` 的 `sceneLabels` 里补一条；表格列与抽屉标签会自动跟着显示。
- 窗口期提示（「启动前需经过：鉴权 → BMS 协商」）直接读 `/chargers/scenes` 返回的
  `preChargePhases`，前端不硬编码阶段名。

### 8.4 红线（与 §5.3 同一套）

1. `scene` 只出现在三处：默认值装配、建档校验/下拉、展示裁剪。业务判断看
   `ProtocolCapabilities`，不看 scene。
2. 订单状态机不加场景专属状态；场景差异只能落进上面七个方法。
3. 握手半状态只写设备影子，`charger_port.status` 仍只有 `IDLE / OCCUPIED / FAULT / CHARGING`
   （`OccupyDecision` 构造器强制）。
4. `charge_order` 不加场景专属列；只对单个场景有意义的字段进扩展表。
5. `if ("AC_CAR".equals(scene))` 命中文件数超过 8 个 → 升级成独立产品线模块，不继续加 if。

### 8.5 自证

照 `TestSceneReverseProofTest` 的写法：**同一个 `ChargingService` / `OccupancyService` 实例，
只换 `charger.scene`**，逐个断言两套流程的四个差异点（插枪准入、ack 后是否 CHARGING、
占位判定、停机超时）。同一实例能跑出两种行为，才说明判定真的从 flow 读，而不是被写死在 service 里。

---

## 9. 观测接口

| 接口 | 权限 | 内容 |
| --- | --- | --- |
| `GET /api/v1/device/protocols` | `simulator:list` | 当前装配了哪些协议、每个协议声明了什么能力 |
| `GET /api/v1/device/flows` | `simulator:list` | 每个场景的时序差异，含自动停规则名 |
| `GET /api/v1/chargers/scenes` | `charger:list` | 建档下拉与展示用的场景清单（`/flows` 的裁剪版） |

三者都直接读注册表，不查库，所以不会出现「下拉里有、启动时却没有」的漂移。真实返回
（2026-10-28 打在标准版实例上，字段按字典序紧凑排版，与浏览器里看到的顺序不同但内容一致）：

```json
// GET /api/v1/device/protocols
[{"bmsHandshake":false,"maxPorts":12,"ota":true,"perPortMetering":true,"portIdModel":"PORT_NUMBER",
  "protocol":"BALAN_MQTT","remoteStart":true,"remoteStop":true,"setThreshold":true,"unlock":true},
 {"bmsHandshake":true,"maxPorts":2,"ota":false,"perPortMetering":true,"portIdModel":"CONNECTOR_ID",
  "protocol":"OCPP16J","remoteStart":true,"remoteStop":true,"setThreshold":false,"unlock":true}]

// GET /api/v1/device/flows
[{"autoStopRules":["amount-limit","time-limit"],"preChargePhases":[],"scene":"TWOWHEEL",
  "startsOnCommandAck":true,"stoppingTimeoutSeconds":15},
 {"autoStopRules":["amount-limit","time-limit"],"preChargePhases":["PLUGGED"],"scene":"AC_CAR",
  "startsOnCommandAck":false,"stoppingTimeoutSeconds":60},
 {"autoStopRules":["amount-limit","time-limit"],
  "preChargePhases":["PLUGGED","AUTHENTICATING","HANDSHAKING","BMS_NEGOTIATING","PRE_CHARGE"],
  "scene":"DC_CAR","startsOnCommandAck":false,"stoppingTimeoutSeconds":90}]

// GET /api/v1/chargers/scenes
[{"preChargePhases":[],"scene":"TWOWHEEL","startsOnCommandAck":true,"stoppingTimeoutSeconds":15},
 {"preChargePhases":["PLUGGED"],"scene":"AC_CAR","startsOnCommandAck":false,"stoppingTimeoutSeconds":60},
 {"preChargePhases":["PLUGGED","AUTHENTICATING","HANDSHAKING","BMS_NEGOTIATING","PRE_CHARGE"],
  "scene":"DC_CAR","startsOnCommandAck":false,"stoppingTimeoutSeconds":90}]
```

排障顺序：**设备上线了但没有任何事件** → 先看 `/protocols` 里有没有 `charger.protocol` 那一项
（没有就是没注册，查装配）；有，再按传输分开查。MQTT 那条查主题拼写与报文能不能被 `decode`
（解不出会落 `Unrecognized`，日志里有原因）；OCPP 那条先看握手过没过——`/ocpp/{identity}` 的握手在
`OcppHandshakeInterceptor` 里就校验 identity 已建档、`charger.protocol=OCPP16J`、全局唯一、以及
开启认证时的 Basic 凭据，任一条不过直接 400/401，日志带 identity。会话表 `OcppSessionRegistry`
只在进程内，**没有查询接口**；`/api/v1/device/ocpp/simulators` 列的是内置模拟桩，不是真实连接。
**新建设备报场景不合法** → 看 `/chargers/scenes`，登记表里没有就是 flow 没注册。

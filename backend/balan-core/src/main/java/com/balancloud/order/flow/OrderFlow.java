package com.balancloud.order.flow;

import com.balancloud.order.model.RateSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * 一个用车场景的订单流程差异点。选择键是 {@code charger.scene}，但实现内部只看设备事实与
 * {@link com.balancloud.device.protocol.ProtocolCapabilities}，不看 scene 字符串。
 *
 * <p>这条约束是有原因的：同一场景可以接不同厂商的桩，能力不一样。
 * 把 scene 当业务能力用（例如 {@code if (scene.equals("AC_CAR")) 允许远程启动}），
 * 遇到只支持扫码后手动启动的交流桩就会做出错误判断。
 *
 * <p>订单状态机本身（PENDING_START → CHARGING → STOPPING → SETTLED/ABNORMAL，见 OrderStatus）
 * 不随场景变：它表达的是「钱和责任的阶段」，不是「电气的阶段」。场景只改这几处：
 * 启动前的准入、CHARGING 之前的阶段、ack 后是否立刻进入充电、结束后的占位判定、自动停条件、兜底超时。
 */
public interface OrderFlow {

    /** 与 {@code charger.scene} 一一对应，全集群唯一；如 TWOWHEEL。 */
    String scene();

    /** 这个场景允许从哪些端口状态启动；不允许时抛业务异常。 */
    void assertStartable(StartContext ctx);

    /** 进入 CHARGING 之前要经过的阶段；两轮返回空列表。 */
    List<PreChargePhase> preChargePhases();

    /** 指令 ack 之后订单是否立刻算「开始充电」。 */
    boolean startsOnCommandAck();

    /**
     * 停止指令 ack 之后订单是否立刻结算。
     *
     * <p>两轮桩的 STOP ack 就是「电已经断了」，可以直接按当前读数结算；直流桩的 ack 只代表
     * 桩接受了停止请求，真实的停止电量在随后的 {@code StopTransaction} 里，提前结算会少算电量，
     * 所以这里返回 false，由设备事件收尾。
     */
    default boolean settlesOnCommandAck() {
        return true;
    }

    /** 订单结束后这个场景怎么判定「车还在占位」。 */
    OccupyDecision resolveOccupy(OccupyContext ctx);

    /** 该场景的自动停条件集合，在金额与时长之外追加（交流桩加目标 SOC，大车加电芯温度）。 */
    List<AutoStopRule> autoStopRules(RateSnapshot snapshot);

    /** 兜底扫描阈值：大车握手慢，STOPPING 超时不能一律 15 秒。 */
    Duration stoppingTimeout();
}

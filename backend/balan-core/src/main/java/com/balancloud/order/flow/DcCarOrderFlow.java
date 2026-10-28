package com.balancloud.order.flow;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.model.RateSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * 直流快充场景（轮充/大车同用一条干线）：OCPP 1.6J 桩的订单流程。
 *
 * <p>与两轮流程的差异只有四处，其余全部复用：
 * <ul>
 *   <li>{@code startsOnCommandAck} 为 false —— RemoteStart 的 Accepted 只代表桩受理了请求，
 *       真正开始充电要等桩插枪、握手、绝缘自检后发来的 {@code StartTransaction}；</li>
 *   <li>{@code settlesOnCommandAck} 为 false —— RemoteStop 的 Accepted 同理，电量以
 *       {@code StopTransaction.meterStop} 为准；</li>
 *   <li>占位费恒不适用 —— 直流桩充满即解锁，没有「车占着位」这个信号；</li>
 *   <li>STOPPING 兜底 90 秒 —— 直流桩断高压、BMS 下电比两轮插座慢，15 秒会误判成超时。</li>
 * </ul>
 *
 * <p>自动停条件与两轮一致（金额优先于时长），因为订单参数本身没有场景差异，差异只在设备侧。
 */
public class DcCarOrderFlow implements OrderFlow {

    static final String SCENE = "DC_CAR";

    static final Duration STOPPING_TIMEOUT = Duration.ofSeconds(90);

    /** 直流桩插枪后要走的半状态，只进设备影子，不写 {@code charger_port.status}。 */
    private static final List<PreChargePhase> PHASES = List.of(
            PreChargePhase.PLUGGED,
            PreChargePhase.AUTHENTICATING,
            PreChargePhase.HANDSHAKING,
            PreChargePhase.BMS_NEGOTIATING,
            PreChargePhase.PRE_CHARGE);

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public void assertStartable(StartContext ctx) {
        // 直流桩正常的启动顺序是「插枪 → Preparing(OCCUPIED) → 远程启动」，所以 OCCUPIED 必须放行。
        if (!ctx.idle() && !ctx.occupied()) {
            throw new ServiceException(400, "直流桩当前不是可启动状态（插枪后等待启动）");
        }
    }

    @Override
    public List<PreChargePhase> preChargePhases() {
        return PHASES;
    }

    @Override
    public boolean startsOnCommandAck() {
        return false;
    }

    @Override
    public boolean settlesOnCommandAck() {
        return false;
    }

    @Override
    public OccupyDecision resolveOccupy(OccupyContext ctx) {
        if (!ctx.portExists()) {
            return OccupyDecision.releaseToIdle();
        }
        if (OccupyDecision.FAULT.equals(ctx.portStatus())) {
            return OccupyDecision.fault();
        }
        // 直流桩不按「车还插着枪」收占位费：结算即释放，避免把充满未拔枪的车计费。
        return OccupyDecision.releaseToIdle();
    }

    @Override
    public List<AutoStopRule> autoStopRules(RateSnapshot snapshot) {
        return List.of(TwoWheelOrderFlow.AMOUNT_LIMIT, TwoWheelOrderFlow.TIME_LIMIT);
    }

    @Override
    public Duration stoppingTimeout() {
        return STOPPING_TIMEOUT;
    }
}

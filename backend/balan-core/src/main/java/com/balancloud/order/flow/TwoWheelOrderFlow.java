package com.balancloud.order.flow;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.order.model.StopReason;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 两轮/多路插座的默认流程，也就是改造前系统里唯一的那套判定。
 *
 * <p>每个方法都对着改造前的代码逐条搬运，Step 7/8 已接线，{@code ChargingService} 与
 * {@code OccupancyService} 都按 {@code charger.scene} 选到这里：
 * <ul>
 *   <li>{@code assertStartable} ← {@code ChargingService.start} 的端口前置集合（IDLE 与 OCCUPIED 可起单）；</li>
 *   <li>{@code startsOnCommandAck} ← 设备对 START 的 ack 即进入 CHARGING；</li>
 *   <li>{@code resolveOccupy} ← {@code OccupancyService.canOccupy} 与拔枪分支；</li>
 *   <li>{@code autoStopRules} ← {@code ChargingService.onMetric} 的金额、时长两个 else-if；</li>
 *   <li>{@code stoppingTimeout} ← {@code sweepStoppingOrdersForTenant} 的 15 秒。</li>
 * </ul>
 */
public class TwoWheelOrderFlow implements OrderFlow {

    static final Duration STOPPING_TIMEOUT = Duration.ofSeconds(15);

    static final AutoStopRule AMOUNT_LIMIT = new AutoStopRule() {
        @Override
        public String name() {
            return "amount-limit";
        }

        @Override
        public Optional<Hit> evaluate(ChargeOrder order, RateSnapshot snapshot) {
            // 与改造前一致：金额判断在前，命中即不再看时长。
            if (order.getTotalFee() != null
                    && order.getTotalFee().compareTo(order.getChargeLimitAmount()) >= 0) {
                return Optional.of(new Hit(StopReason.AMOUNT_LIMIT, "达到金额上限"));
            }
            return Optional.empty();
        }
    };

    static final AutoStopRule TIME_LIMIT = new AutoStopRule() {
        @Override
        public String name() {
            return "time-limit";
        }

        @Override
        public Optional<Hit> evaluate(ChargeOrder order, RateSnapshot snapshot) {
            if (order.getDurationSeconds() != null
                    && order.getDurationSeconds() >= order.getMaxDurationMinutes() * 60) {
                return Optional.of(new Hit(StopReason.TIME_LIMIT, "达到最大充电时长"));
            }
            return Optional.empty();
        }
    };

    @Override
    public String scene() {
        return OrderFlowRegistry.DEFAULT_SCENE;
    }

    @Override
    public void assertStartable(StartContext ctx) {
        if (!ctx.idle() && !ctx.occupied()) {
            throw new ServiceException(400, "端口当前不是空闲状态，无法启动充电");
        }
    }

    @Override
    public List<PreChargePhase> preChargePhases() {
        return List.of();
    }

    @Override
    public boolean startsOnCommandAck() {
        return true;
    }

    @Override
    public OccupyDecision resolveOccupy(OccupyContext ctx) {
        if (!ctx.portExists()) {
            return OccupyDecision.releaseToIdle();
        }
        if (OccupyDecision.FAULT.equals(ctx.portStatus())) {
            // 故障类停止原因由调用方先转成 FAULT，这里不再把枪插着的端口拉回占用。
            return OccupyDecision.fault();
        }
        boolean alreadyOccupied = OccupyDecision.OCCUPIED.equals(ctx.portStatus());
        if (alreadyOccupied) {
            // 占位中拔枪要先结掉已累计的占位费再释放；没拔枪就继续占着。
            return ctx.pluggedIn()
                    ? OccupyDecision.keepOccupied() : OccupyDecision.releaseToIdleAfterSettle();
        }
        if (ctx.occupancyEnabled() && ctx.appUserId() != null && ctx.pluggedIn()) {
            return OccupyDecision.keepOccupied();
        }
        return OccupyDecision.releaseToIdle();
    }

    /**
     * 固定按「金额 → 时长」返回；调用方取第一条命中，与改造前的 else-if 等价。
     * 两轮的自动停只看订单参数，不需要 {@code RateSnapshot}。
     */
    @Override
    public List<AutoStopRule> autoStopRules(RateSnapshot snapshot) {
        return List.of(AMOUNT_LIMIT, TIME_LIMIT);
    }

    @Override
    public Duration stoppingTimeout() {
        return STOPPING_TIMEOUT;
    }
}

package com.balancloud.order.flow;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.model.RateSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * 四轮交流桩场景（OCPP 1.6J 交流桩）：与两轮共用订单干线，差异只有启动/结束的时序与超时。
 *
 * <p>与两轮的相同点：插枪信号有效（占位费可用），所以 {@link #resolveOccupy} 逐条沿用两轮六分支，
 * 不另写一套判定；自动停仍是金额优先于时长。
 *
 * <p>与两轮的不同点：a{@code RemoteStartTransaction} / {@code RemoteStopTransaction} 的 Accepted
 * 都只代表桩受理，真正的开始与结束电量分别在 {@code StartTransaction} / {@code StopTransaction} 里；
 * 交流桩断输出比两轮慢、比直流快，STOPPING 兜底取 60 秒。
 */
public class AcCarOrderFlow implements OrderFlow {

    public static final String SCENE = "AC_CAR";

    static final Duration STOPPING_TIMEOUT = Duration.ofSeconds(60);

    /** 交流桩只有「插枪 → 启动」这一小段，不编造握手/BMS 阶段。 */
    private static final List<PreChargePhase> PHASES = List.of(PreChargePhase.PLUGGED);

    private final TwoWheelOrderFlow occupy = new TwoWheelOrderFlow();

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public void assertStartable(StartContext ctx) {
        // 扫码启动时桩可能还报 Available，插枪后报 Preparing(OCCUPIED)，两种都要放行。
        if (!ctx.idle() && !ctx.occupied()) {
            throw new ServiceException(400, "交流桩当前不是可启动状态（插枪后等待启动）");
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
        return occupy.resolveOccupy(ctx);
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

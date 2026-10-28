package com.balancloud.order.flow;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.model.RateSnapshot;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 只活在测试里的第三个场景：交流/直流车桩将来长什么样，先在这里用最小规则占位。
 *
 * <p>它存在的唯一目的，是证明「新增场景不改 {@code ChargingService} / {@code OccupancyService}」：
 * 注册它之后，{@code charger.scene='TEST_SCENE'} 的设备会走完全不同的启动准入、
 * 启动阶段、占位判定与兜底超时，而这两个服务一行都不用动。
 *
 * <p>规则刻意与两轮全部相反：必须插枪才让启动、ack 之后不算开始充电、结束后永不产生占位费、
 * 停止超时 45 秒。
 */
class TestSceneOrderFlow implements OrderFlow {

    static final String SCENE = "TEST_SCENE";

    static final Duration STOPPING_TIMEOUT = Duration.ofSeconds(45);

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public void assertStartable(StartContext ctx) {
        if (!"1".equals(ctx.plugged())) {
            throw new ServiceException(400, "该场景必须插枪后才能启动");
        }
        if (!ctx.idle() && !ctx.occupied()) {
            throw new ServiceException(400, "端口当前不是空闲状态，无法启动充电");
        }
    }

    @Override
    public List<PreChargePhase> preChargePhases() {
        return List.of(PreChargePhase.AUTHENTICATING, PreChargePhase.HANDSHAKING,
                PreChargePhase.BMS_NEGOTIATING);
    }

    @Override
    public boolean startsOnCommandAck() {
        return false;
    }

    @Override
    public OccupyDecision resolveOccupy(OccupyContext ctx) {
        if (OccupyDecision.FAULT.equals(ctx.portStatus())) {
            return OccupyDecision.fault();
        }
        // 车桩场景不按插枪判占位：拔枪与否都直接释放，也就没有“先结占位费再释放”这回事。
        return OccupyDecision.releaseToIdle();
    }

    @Override
    public List<AutoStopRule> autoStopRules(RateSnapshot snapshot) {
        return List.of(SOC_TARGET);
    }

    @Override
    public Duration stoppingTimeout() {
        return STOPPING_TIMEOUT;
    }

    /** 车桩才会有的自动停条件，这里用永不命中的空实现占位。 */
    private static final AutoStopRule SOC_TARGET = new AutoStopRule() {
        @Override
        public String name() {
            return "test-soc-target";
        }

        @Override
        public Optional<Hit> evaluate(ChargeOrder order, RateSnapshot snapshot) {
            return Optional.empty();
        }
    };
}

package com.balancloud.order.flow;

import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.order.model.StopReason;

import java.util.Optional;

/**
 * 一条「平台该主动叫停」的判断，输入是已经刷过测点的订单。
 *
 * <p>现有两条（金额上限、时长上限）属于所有场景共用的参数级判断，会原样搬进
 * {@code TwoWheelOrderFlow.autoStopRules}；交流桩之后加「达到目标 SOC」，大型车加「电芯超温」，
 * 都是往这个列表里追加一条而不是往 {@code ChargingService.onMetric} 里再叠一层 if-else。
 */
public interface AutoStopRule {

    /** 进日志用，必须稳定且唯一；命中时写在命中详情里。 */
    String name();

    Optional<Hit> evaluate(ChargeOrder order, RateSnapshot snapshot);

    record Hit(StopReason reason, String detail) {
    }
}

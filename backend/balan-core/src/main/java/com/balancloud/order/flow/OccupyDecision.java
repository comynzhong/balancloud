package com.balancloud.order.flow;

import java.util.List;

/**
 * 占位判定的结果：端口落到哪个状态，以及要不要先结算一笔占位费。
 *
 * <p>状态词汇被构造器锁死在三个值（加上运行中的 CHARGING 共四个），
 * 目的是让「顺手加个握手态」在编译期就加不进来——见 §45.1 事实 2：{@code charger_port.status}
 * 的取值一直是靠各处约定而不是靠代码约束的。
 */
public record OccupyDecision(String portStatus, boolean settleFirst) {

    public static final String IDLE = "IDLE";
    public static final String OCCUPIED = "OCCUPIED";
    public static final String FAULT = "FAULT";

    private static final List<String> ALLOWED = List.of(IDLE, OCCUPIED, FAULT);

    public OccupyDecision {
        if (!ALLOWED.contains(portStatus)) {
            throw new IllegalArgumentException("端口状态只能写 " + ALLOWED + "，收到 " + portStatus);
        }
    }

    public static OccupyDecision releaseToIdle() {
        return new OccupyDecision(IDLE, false);
    }

    public static OccupyDecision releaseToIdleAfterSettle() {
        return new OccupyDecision(IDLE, true);
    }

    public static OccupyDecision keepOccupied() {
        return new OccupyDecision(OCCUPIED, false);
    }

    public static OccupyDecision fault() {
        return new OccupyDecision(FAULT, false);
    }
}

package com.balancloud.order.model;

import com.balancloud.common.utils.SecurityUtils;

public record ChargingActor(Type type, Long id, String name, Long appUserId) {

    public enum Type {
        OPERATOR,
        APP_USER,
        DEVICE
    }

    /** 设备侧自发建单（即插即充）：订单属于车主 appUserId，但操作者是桩。 */
    public static ChargingActor device(Long appUserId) {
        return new ChargingActor(Type.DEVICE, null, "device", appUserId);
    }

    public static ChargingActor operator() {
        return new ChargingActor(Type.OPERATOR, SecurityUtils.getUserId(), SecurityUtils.getUsername(), null);
    }

    public static ChargingActor appUser(Long userId, String name) {
        return new ChargingActor(Type.APP_USER, userId,
                name == null || name.isBlank() ? "小程序用户" + userId : name, userId);
    }

    public boolean isOperator() {
        return type == Type.OPERATOR;
    }
}

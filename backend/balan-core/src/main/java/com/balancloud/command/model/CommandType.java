package com.balancloud.command.model;

public enum CommandType {
    START,
    STOP,
    UNLOCK,
    SET_THRESHOLD;

    public static CommandType parse(String value) {
        try {
            return CommandType.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("指令类型不合法");
        }
    }
}

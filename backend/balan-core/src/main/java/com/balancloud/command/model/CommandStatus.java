package com.balancloud.command.model;

public enum CommandStatus {
    SENT,
    ACKED,
    FAILED,
    TIMEOUT;

    public boolean isTerminal() {
        return this != SENT;
    }
}

package com.balancloud.device.protocol;

import com.balancloud.command.model.CommandType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台想对设备做的一件事，与传输方言无关。
 *
 * <p>{@code action} 决定适配器把它映射成哪个下行事件段：{@value #ACTION_CMD} 走指令状态机，
 * {@value #ACTION_OTA} 走固件任务。两者的正文形状不同（指令有 cmdId/portNo 信封，OTA 是扁平字段），
 * 但都只有一条通道，所以放在同一个 record 里而不是两个类型。
 */
public record PlatformCommand(String action,
                              String cmdId,
                              String deviceNo,
                              Integer portNo,
                              CommandType type,
                              Map<String, Object> params,
                              long ts) {

    public static final String ACTION_CMD = "cmd";
    public static final String ACTION_OTA = "ota";

    public static PlatformCommand start(String cmdId, String deviceNo, Integer portNo, Map<String, Object> params) {
        return control(cmdId, deviceNo, portNo, CommandType.START, params);
    }

    public static PlatformCommand control(String cmdId, String deviceNo, Integer portNo,
                                          CommandType type, Map<String, Object> params) {
        return new PlatformCommand(ACTION_CMD, cmdId, deviceNo, portNo, type,
                params == null ? Map.of() : new LinkedHashMap<>(params), System.currentTimeMillis());
    }

    public static PlatformCommand ota(String deviceNo, Map<String, Object> fields) {
        return new PlatformCommand(ACTION_OTA, null, deviceNo, null, null,
                fields == null ? Map.of() : new LinkedHashMap<>(fields), System.currentTimeMillis());
    }
}

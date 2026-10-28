package com.balancloud.device.protocol;

import java.util.List;
import java.util.Optional;

/**
 * 一种设备协议的语义层实现：把传输帧解成平台事件，把平台指令编成传输帧。
 *
 * <p>业务代码（订单、计费、告警、看板）只允许接触 {@link PlatformEvent} 与 {@link PlatformCommand}，
 * 不允许再出现事件名字符串或报文 JSON 字段名。判断「这台桩能不能远程启动」要看
 * {@link ProtocolCapabilities}，不是看 {@code protocol} 字符串等不等。
 *
 * <p>本接口在 {@code balan-core}（AGPL）里，第三方适配器据此实现即可，core 业务代码不用改；
 * 非 MQTT 传输（OCPP WebSocket、纯 TCP 定界）要先补传输层，见 MVP 文档 §42.4。
 */
public interface DeviceProtocolAdapter {

    /** 与 {@code charger.protocol} 一一对应，全集群唯一；如 BALAN_MQTT。 */
    String protocol();

    ProtocolCapabilities capabilities();

    /** 一条传输帧 → 0..n 个平台事件；解不出来返回空集合，不抛业务异常。 */
    List<PlatformEvent> decode(RawFrame frame);

    /** 平台指令 → 传输帧；主题与正文由适配器决定，平台不拼字符串。 */
    RawFrame encode(PlatformCommand command);

    /** 幂等键：二进制或带时间戳的方言不能靠正文哈希，交由适配器给；缺省回落到正文指纹。 */
    default Optional<String> dedupKey(RawFrame frame) {
        return Optional.empty();
    }
}

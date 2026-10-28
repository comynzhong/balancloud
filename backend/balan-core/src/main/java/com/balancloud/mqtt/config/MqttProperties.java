package com.balancloud.mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "balan.mqtt")
public class MqttProperties {

    private boolean enabled = true;
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "balancloud-platform";
    private String username;
    private String password;
    private int qos = 1;

    /** EMQX 共享订阅消费组；留空退回普通订阅（多实例会各自收到全量报文）。 */
    private String sharedGroup = "balancloud";

    /** 同一报文在窗口内只处理一次，兜住 QoS1 重投与订阅组重分配。 */
    private boolean dedupeEnabled = true;

    private int dedupeTtlSeconds = 300;
}

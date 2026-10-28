package com.balancloud.framework.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多实例部署参数。这些都是启动期生效的部署面配置，不进 sys_config：
 * 改了必须重启才成立，放进热生效配置反而会变成「改了却不生效」的假开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "balan.cluster")
public class ClusterProperties {

    /** 实例标识，留空则按主机名 + 随机串自动生成。 */
    private String instanceId = "";

    /** 事件流跨实例广播的 Redis 频道。 */
    private String wsChannel = "balancloud:ws:event";

    /** 系统配置写入后通知其他实例重载缓存的 Redis 频道。 */
    private String configChannel = "balan:config:reload";

    /** OCPP 下行跨实例转发：指令落在 A 实例、充电桩 WebSocket 连在 B 实例时走这里。 */
    private String ocppDownChannel = "balan:ocpp:down";

    /** 关闭后事件只投本实例连接，退回改版前的单机行为。 */
    private boolean wsBroadcast = true;
}

package com.balancloud.framework.cluster;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 当前进程在集群里的身份：MQTT 客户端 ID、Redis 锁持有者与广播回环判断都用它。
 */
@Component
public class ClusterNode {

    private final String instanceId;

    public ClusterNode(ClusterProperties properties) {
        this.instanceId = resolve(properties.getInstanceId());
    }

    public String id() {
        return instanceId;
    }

    public boolean isSelf(String otherInstanceId) {
        return instanceId.equals(otherInstanceId);
    }

    private static String resolve(String configured) {
        String random = UUID.randomUUID().toString().substring(0, 8);
        if (StringUtils.hasText(configured)) {
            return sanitize(configured.trim(), random);
        }
        return hostname() + "-" + random;
    }

    /** MQTT 客户端 ID 只允许 ASCII 可见字符，主机名可能带中文或下划线。 */
    private static String sanitize(String raw, String random) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "-");
        if (cleaned.replaceAll("[.-]", "").isEmpty()) {
            return "node-" + random;
        }
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }

    private static String hostname() {
        try {
            String cleaned = InetAddress.getLocalHost().getHostName().replaceAll("[^A-Za-z0-9._-]", "-");
            return cleaned.replaceAll("[.-]", "").isEmpty() ? "node" : cleaned;
        } catch (Exception e) {
            return "node";
        }
    }
}

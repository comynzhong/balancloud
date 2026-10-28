package com.balancloud.order.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按 {@code scene} 建索引的流程注册表；同一场景出现两个实现即启动失败，与
 * {@link com.balancloud.device.protocol.ProtocolAdapterRegistry} 同构、同一套纪律。
 *
 * <p>scene 为空时按 {@value #DEFAULT_SCENE} 处理：V27 之前的存量 {@code charger} 行没有这一列，
 * 迁移会给默认值，但内存里手造的实体对象不会有，回落比抛异常更贴近现状。
 *
 * <p>由 {@link OrderFlowAutoConfiguration} 注册成 bean：注册表按「所有 {@link OrderFlow} 实现」建索引。
 */
public class OrderFlowRegistry {

    public static final String DEFAULT_SCENE = "TWOWHEEL";

    private final Map<String, OrderFlow> byScene;

    public OrderFlowRegistry(List<OrderFlow> flows) {
        Map<String, OrderFlow> index = new LinkedHashMap<>();
        for (OrderFlow flow : (flows == null ? List.<OrderFlow>of() : flows)) {
            String scene = normalize(flow.scene());
            OrderFlow previous = index.putIfAbsent(scene, flow);
            if (previous != null) {
                throw new IllegalStateException("场景流程重复注册: " + scene
                        + "（" + previous.getClass().getName() + " 与 " + flow.getClass().getName() + "）");
            }
        }
        this.byScene = Collections.unmodifiableMap(index);
    }

    public OrderFlow require(String scene) {
        OrderFlow flow = byScene.get(normalize(scene));
        if (flow == null) {
            throw new IllegalStateException("场景流程未注册: " + scene + "，已注册场景 " + available());
        }
        return flow;
    }

    public Optional<OrderFlow> find(String scene) {
        return Optional.ofNullable(byScene.get(normalize(scene)));
    }

    public Set<String> available() {
        return byScene.keySet();
    }

    private static String normalize(String scene) {
        return scene == null || scene.isBlank() ? DEFAULT_SCENE : scene.trim().toUpperCase();
    }
}

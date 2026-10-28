package com.balancloud.device.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备编号全平台唯一（§48 R3）：OCPP 用 device_no 当连接路径，建档时若只查本租户，
 * A 租户和 B 租户可以各建一台同名桩，握手时无法判定归属。
 *
 * <p>这里钉的是「查重 SQL 必须绕过租户拦截器」。方法上的注解一旦被删，跨租户重名会静默放行，
 * 而握手侧要到连接时才 409，排查成本高得多。
 */
class ChargerMapperTenantScopeTest {

    @Test
    void deviceNoUniquenessQueriesIgnoreTheTenantInterceptor() throws Exception {
        assertIgnoresTenant("countByDeviceNoIncludingDeleted", String.class);
        assertIgnoresTenant("countByDeviceNoExcludingId", String.class, Long.class);
        assertIgnoresTenant("selectIdentityRefsByDeviceNo", String.class);
    }

    private void assertIgnoresTenant(String method, Class<?>... parameterTypes) throws Exception {
        InterceptorIgnore ignore = ChargerMapper.class.getMethod(method, parameterTypes)
                .getAnnotation(InterceptorIgnore.class);
        assertThat(ignore)
                .as(method + " 必须 @InterceptorIgnore(tenantLine = \"true\")")
                .isNotNull();
        assertThat(ignore.tenantLine()).isEqualTo("true");
    }
}

package com.balancloud.system.service;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.spi.TenantConfigSource;
import com.balancloud.spi.defaults.NoopTenantConfigSource;
import com.balancloud.system.domain.SysConfig;
import com.balancloud.system.mapper.SysConfigGroupMapper;
import com.balancloud.system.mapper.SysConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §36.7 的取值出口：租户级覆盖只在读的一侧生效，配置的定义与全局写入仍按 sys_config 走。
 */
class SystemConfigTenantOverrideTest {

    private static final String KEY = "brand.platformName";

    private final List<SysConfig> rows = new ArrayList<>(List.of(row("衡电云控")));
    private SysConfigMapper configMapper;

    @BeforeEach
    void setUp() {
        configMapper = mock(SysConfigMapper.class);
        when(configMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(rows));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private SystemConfigService service(TenantConfigSource source) {
        return new SystemConfigService(configMapper, mock(SysConfigGroupMapper.class), new ObjectMapper(),
                mock(StringRedisTemplate.class), new ClusterProperties(), mock(ClusterNode.class), source,
                "community");
    }

    @Test
    void overrideOnlyChangesTheValueSeenByTheSessionTenant() {
        SystemConfigService service = service((tenantId, configKey) ->
                Long.valueOf(2L).equals(tenantId) && KEY.equals(configKey) ? "租户二品牌" : null);
        service.reload();

        TenantContext.use(2L);
        assertThat(service.getString(KEY, "")).isEqualTo("租户二品牌");

        TenantContext.use(1L);
        assertThat(service.getString(KEY, "")).isEqualTo("衡电云控");
    }

    @Test
    void communityDefaultKeepsReadingGlobalValue() {
        SystemConfigService service = service(new NoopTenantConfigSource());
        service.reload();
        TenantContext.use(2L);

        assertThat(service.getString(KEY, "")).isEqualTo("衡电云控");
    }

    @Test
    void globalWriteIsComparedAgainstTheGlobalValueNotTheOverride() {
        SystemConfigService service = service((tenantId, configKey) -> "租户二品牌");
        service.reload();
        TenantContext.use(2L);

        service.update(Map.of(KEY, "新平台名"));

        ArgumentCaptor<SysConfig> patch = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).updateById(patch.capture());
        assertThat(patch.getValue().getConfigValue()).isEqualTo("新平台名");
    }

    private static SysConfig row(String value) {
        SysConfig row = new SysConfig();
        row.setConfigId(1L);
        row.setConfigKey(KEY);
        row.setConfigName("平台名称");
        row.setConfigValue(value);
        row.setValueType("STRING");
        row.setEditable("1");
        row.setIsSystem("1");
        row.setStatus("0");
        row.setMaxLength(40);
        row.setSort(1);
        return row;
    }
}

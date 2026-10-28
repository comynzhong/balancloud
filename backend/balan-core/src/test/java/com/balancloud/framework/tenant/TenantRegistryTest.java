package com.balancloud.framework.tenant;

import com.balancloud.framework.tenant.mapper.TenantDirectoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRegistryTest {

    private TenantDirectoryMapper directoryMapper;
    private TenantRegistry registry;

    @BeforeEach
    void setUp() {
        directoryMapper = mock(TenantDirectoryMapper.class);
        registry = new TenantRegistry(directoryMapper);
    }

    @Test
    void listingsAreCachedBetweenTicks() {
        when(directoryMapper.selectActiveTenantIds()).thenReturn(List.of(1L, 2L));

        assertThat(registry.activeTenantIds()).containsExactly(1L, 2L);
        assertThat(registry.activeTenantIds()).containsExactly(1L, 2L);

        verify(directoryMapper).selectActiveTenantIds();
    }

    /** 新建租户要立刻进循环，否则它的定时清扫要等缓存过期才开始。 */
    @Test
    void refreshDropsTheCache() {
        when(directoryMapper.selectActiveTenantIds()).thenReturn(List.of(1L), List.of(1L, 2L));

        assertThat(registry.activeTenantIds()).containsExactly(1L);
        registry.refresh();

        assertThat(registry.activeTenantIds()).containsExactly(1L, 2L);
        verify(directoryMapper, times(2)).selectActiveTenantIds();
    }

    @Test
    void duplicatesAndMissingIdsAreNormalized() {
        when(directoryMapper.selectActiveTenantIds()).thenReturn(Arrays.asList(2L, null, 2L, 3L));

        assertThat(registry.activeTenantIds()).containsExactly(2L, 3L);
    }

    @Test
    void anEmptyDirectoryStillCaresForTheDefaultTenant() {
        when(directoryMapper.selectActiveTenantIds()).thenReturn(Collections.emptyList());

        assertThat(registry.activeTenantIds()).containsExactly(TenantContext.DEFAULT_TENANT_ID);
        assertThat(registry.activeCount()).isEqualTo(1);
    }

    /** 注册表查不到不能让整轮任务抛异常：退回默认租户，单机部署照常跑。 */
    @Test
    void directoryOutageFallsBackToTheDefaultTenant() {
        when(directoryMapper.selectActiveTenantIds()).thenThrow(new IllegalStateException("mysql down"));

        assertThat(registry.activeTenantIds()).containsExactly(TenantContext.DEFAULT_TENANT_ID);
    }
}

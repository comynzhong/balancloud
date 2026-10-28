package com.balancloud.system.service;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.spi.defaults.NoopTenantConfigSource;
import com.balancloud.system.domain.SysConfig;
import com.balancloud.system.mapper.SysConfigGroupMapper;
import com.balancloud.system.mapper.SysConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置的多实例一致性：写入方刷新本机缓存并广播失效，其余实例重载自己的缓存。
 */
class SystemConfigClusterInvalidationTest {

    private SysConfigMapper configMapper;
    private StringRedisTemplate redisTemplate;
    private ClusterNode clusterNode;
    private List<SysConfig> rows;
    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(SysConfigMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        clusterNode = mock(ClusterNode.class);
        SysConfigGroupMapper groupMapper = mock(SysConfigGroupMapper.class);
        rows = new ArrayList<>(List.of(brandName("衡电云控")));
        when(configMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(rows));
        when(groupMapper.selectList(any())).thenReturn(List.of());
        when(clusterNode.id()).thenReturn("node-a");
        service = new SystemConfigService(configMapper, groupMapper, new ObjectMapper(),
                redisTemplate, new ClusterProperties(), clusterNode, new NoopTenantConfigSource(), "community");
    }

    private SysConfig brandName(String value) {
        SysConfig row = new SysConfig();
        row.setConfigId(1L);
        row.setConfigKey("brand.platformName");
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

    @Test
    void ownBroadcastIsIgnored() {
        service.reload();
        service.reloadFromPeer("node-a");
        verify(configMapper, times(1)).selectList(any());
    }

    @Test
    void peerBroadcastReloadsLocalCache() {
        service.reload();
        rows.set(0, brandName("换名了"));
        service.reloadFromPeer("node-b");
        assertThat(service.getString("brand.platformName", "")).isEqualTo("换名了");
        verify(configMapper, times(2)).selectList(any());
    }

    @Test
    void writeBroadcastsInvalidationWithInstanceId() {
        service.reload();
        service.update(Map.of("brand.platformName", "衡电云控二号"));
        verify(configMapper).updateById(any(SysConfig.class));
        verify(redisTemplate).convertAndSend("balan:config:reload", "node-a");
    }

    @Test
    void broadcastFailureStillKeepsLocalCacheFresh() {
        service.reload();
        doThrow(new IllegalStateException("redis down")).when(redisTemplate)
                .convertAndSend(any(), any());
        rows.set(0, brandName("衡电云控三号"));
        service.update(Map.of("brand.platformName", "衡电云控三号"));
        verify(configMapper).updateById(any(SysConfig.class));
        // update() 内部的 reload() 重新读库，广播失败不影响本机生效
        assertThat(service.getString("brand.platformName", "")).isEqualTo("衡电云控三号");
    }
}

package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.balancloud.app.vo.AppStationVo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.rate.service.RateService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 距离与排序口径来自 geo.js 的 node 实测基准（tmp/geo-js-baseline.js），不是按公式推算（§55.3）。
 */
class AppStationServiceDistanceTest {

    private static final BigDecimal USER_LAT = decimal("30.2741");
    private static final BigDecimal USER_LNG = decimal("120.1551");

    private StationMapper stationMapper;
    private AppStationService service;

    static {
        // LambdaQueryWrapper 取列名要 MP 的表信息缓存，纯 mock 测试里没有 SqlSessionFactory，这里手工装一次
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.balancloud.device.mapper.StationMapper");
        TableInfoHelper.initTableInfo(assistant, Station.class);
    }

    @BeforeEach
    void setUp() {
        stationMapper = mock(StationMapper.class);
        ChargerMapper chargerMapper = mock(ChargerMapper.class);
        when(chargerMapper.selectStatsByStationIds(anyList())).thenReturn(List.of());
        service = new AppStationService(stationMapper, chargerMapper, mock(ChargerPortMapper.class),
                mock(DeviceShadowService.class), mock(RateService.class));
    }

    @Test
    void nearFirstAndStationsWithoutCoordinatesLast() {
        when(stationMapper.selectList(any())).thenReturn(List.of(
                station(1L, "远站", decimal("31.2304000"), decimal("121.4737000")),
                station(2L, "无坐标站", null, null),
                station(3L, "近站", decimal("30.2800000"), decimal("120.1600000")),
                station(4L, "中站", decimal("30.2500000"), decimal("120.1500000"))));

        List<AppStationVo> result = service.list(null, USER_LAT.doubleValue(), USER_LNG.doubleValue());

        assertThat(result).extracting(AppStationVo::getName)
                .containsExactly("近站", "中站", "远站", "无坐标站");
        assertThat(result).extracting(AppStationVo::getDistanceMeters)
                .containsExactly(807L, 2724L, 164874L, null);
    }

    @Test
    void sameDistanceKeepsQueryOrderBecauseNullsOnlyAffectTheTail() {
        when(stationMapper.selectList(any())).thenReturn(List.of(
                station(5L, "同距乙", decimal("30.2800000"), decimal("120.1600000")),
                station(6L, "同距甲", decimal("30.2800000"), decimal("120.1600000")),
                station(7L, "无坐标乙", null, null),
                station(8L, "无坐标甲", null, decimal("120.1551"))));

        List<AppStationVo> result = service.list(null, USER_LAT.doubleValue(), USER_LNG.doubleValue());

        assertThat(result).extracting(AppStationVo::getId).containsExactly(5L, 6L, 7L, 8L);
    }

    @Test
    void withoutUserCoordinatesNothingIsMeasuredAndOrderIsUnchanged() {
        when(stationMapper.selectList(any())).thenReturn(List.of(
                station(2L, "乙", decimal("30.2800000"), decimal("120.1600000")),
                station(1L, "甲", decimal("30.2500000"), decimal("120.1500000"))));

        List<AppStationVo> result = service.list(null, null, null);

        assertThat(result).extracting(AppStationVo::getId).containsExactly(2L, 1L);
        assertThat(result).allSatisfy(vo -> assertThat(vo.getDistanceMeters()).isNull());
    }

    @Test
    void halfCoordinateAndOutOfRangeRejected() {
        assertThatThrownBy(() -> service.list(null, 30.27, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("经纬度需成对提供");
        assertThatThrownBy(() -> service.list(null, null, 120.15))
                .isInstanceOf(ServiceException.class)
                .hasMessage("经纬度需成对提供");
        assertThatThrownBy(() -> service.list(null, 91d, 120.15))
                .isInstanceOf(ServiceException.class)
                .hasMessage("经纬度超出范围");
        assertThatThrownBy(() -> service.list(null, 30.27, 180.5))
                .isInstanceOf(ServiceException.class)
                .hasMessage("经纬度超出范围");
    }

    @Test
    void keywordFilterStillReachesTheQuery() {
        when(stationMapper.selectList(any())).thenReturn(List.of());

        service.list("西湖", 30.2741, 120.1551);

        ArgumentCaptor<Wrapper<Station>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stationMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("status").contains("LIKE");
    }

    private static Station station(Long id, String name, BigDecimal lat, BigDecimal lng) {
        Station station = new Station();
        station.setId(id);
        station.setName(name);
        station.setStatus("ACTIVE");
        station.setLatitude(lat);
        station.setLongitude(lng);
        return station;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}

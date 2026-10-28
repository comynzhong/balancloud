package com.balancloud.device.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.vo.ChargerGroupStats;
import com.balancloud.device.vo.DeviceIdentityRef;
import com.balancloud.device.vo.StationStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ChargerMapper extends BaseMapper<Charger> {

    @Select("""
            SELECT id, station_id, device_no, name, device_type, port_count, protocol, scene, start_method,
                   firmware_version, rated_voltage, rated_power_kw, over_temp_c, over_current_a, online_status,
                   last_online_at, last_metric_at, is_simulated, remark, create_by, create_time,
                   update_by, update_time, del_flag
            FROM charger
            WHERE device_no = #{deviceNo} AND del_flag = '0'
            LIMIT 1
            """)
    Charger selectByDeviceNo(@Param("deviceNo") String deviceNo);

    /**
     * 跨租户反查设备归属：MQTT 上行没有会话，必须先找到设备真正的租户再设置上下文。
     * device_no 只在租户内唯一，所以这里可能返回多个租户，由调用方判歧义。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id FROM charger WHERE device_no = #{deviceNo} AND del_flag = '0'")
    List<Long> selectTenantIdsByDeviceNo(@Param("deviceNo") String deviceNo);

    /** 报文解码要用的协议列；同样要跨租户查，所以必须忽略租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT protocol FROM charger WHERE device_no = #{deviceNo} AND del_flag = '0' LIMIT 1")
    String selectProtocolByDeviceNo(@Param("deviceNo") String deviceNo);

    /**
     * OCPP 握手用：device_no 全局唯一是这一版的口径，返回多行说明存量数据有跨租户重名，
     * 调用方必须拒绝连接而不是挑一个。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id AS tenantId, station_id AS stationId, device_no AS deviceNo,
                   protocol, scene
            FROM charger
            WHERE device_no = #{deviceNo} AND del_flag = '0'
            """)
    List<DeviceIdentityRef> selectIdentityRefsByDeviceNo(@Param("deviceNo") String deviceNo);

    @Select("SELECT COUNT(*) FROM charger WHERE station_id = #{stationId} AND del_flag = '0'")
    long countByStationId(@Param("stationId") Long stationId);

    /** 设备编号全平台唯一：OCPP 用 device_no 当连接路径，跨租户重名会让握手无法判定归属。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM charger WHERE device_no = #{deviceNo}")
    long countByDeviceNoIncludingDeleted(@Param("deviceNo") String deviceNo);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM charger WHERE device_no = #{deviceNo} AND id <> #{id}")
    long countByDeviceNoExcludingId(@Param("deviceNo") String deviceNo, @Param("id") Long id);

    @Select("""
            SELECT id, station_id, device_no, name, device_type, port_count, protocol, scene, start_method,
                   firmware_version, rated_voltage, rated_power_kw, over_temp_c, over_current_a, online_status,
                   last_online_at, last_metric_at, is_simulated, remark, create_by, create_time,
                   update_by, update_time, del_flag
            FROM charger
            WHERE del_flag = '0' AND is_simulated = 1
            ORDER BY id
            """)
    List<Charger> selectSimulated();

    @Select("""
            SELECT * FROM charger
            WHERE del_flag = '0' AND online_status = 'ONLINE'
              AND COALESCE(last_metric_at, last_online_at, update_time) < #{threshold}
            """)
    List<Charger> selectStaleOnline(@Param("threshold") LocalDateTime threshold);

    @Select("""
            <script>
            SELECT c.station_id AS stationId,
                   COUNT(DISTINCT c.id) AS deviceCount,
                   COUNT(DISTINCT CASE WHEN c.online_status = 'ONLINE' THEN c.id END) AS onlineDeviceCount,
                   COUNT(DISTINCT p.id) AS portCount,
                   COUNT(DISTINCT CASE WHEN p.status = 'CHARGING' THEN p.id END) AS chargingPortCount,
                   COUNT(DISTINCT CASE WHEN p.status = 'FAULT' THEN p.id END) AS faultPortCount
            FROM charger c
            LEFT JOIN charger_port p ON p.charger_id = c.id
            WHERE c.del_flag = '0'
              AND c.station_id IN
              <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY c.station_id
            </script>
            """)
    List<StationStats> selectStatsByStationIds(@Param("stationIds") List<Long> stationIds);

    @Select("""
            <script>
            SELECT COALESCE(NULLIF(c.group_name, ''), '未分组') AS groupName,
                   COUNT(DISTINCT c.id) AS deviceCount,
                   COUNT(DISTINCT CASE WHEN c.online_status = 'ONLINE' THEN c.id END) AS onlineDeviceCount,
                   COUNT(DISTINCT p.id) AS portCount,
                   COUNT(DISTINCT CASE WHEN p.status = 'CHARGING' THEN p.id END) AS chargingPortCount,
                   COUNT(DISTINCT CASE WHEN p.status = 'FAULT' THEN p.id END) AS faultPortCount
            FROM charger c
            LEFT JOIN charger_port p ON p.charger_id = c.id
            WHERE c.del_flag = '0'
              AND c.station_id IN
              <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY COALESCE(NULLIF(c.group_name, ''), '未分组')
            ORDER BY deviceCount DESC, groupName
            </script>
            """)
    List<ChargerGroupStats> selectGroupStatsByStationIds(@Param("stationIds") List<Long> stationIds);
}

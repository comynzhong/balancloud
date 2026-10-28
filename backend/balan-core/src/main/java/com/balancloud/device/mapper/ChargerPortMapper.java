package com.balancloud.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.device.domain.ChargerPort;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChargerPortMapper extends BaseMapper<ChargerPort> {

    @Select("""
            SELECT id, charger_id, port_no, rated_current_a, rated_power_kw, status,
                   current_order_no, last_metric_at
            FROM charger_port
            WHERE charger_id = #{chargerId}
            ORDER BY port_no
            """)
    List<ChargerPort> selectByChargerId(@Param("chargerId") Long chargerId);

    @Delete("DELETE FROM charger_port WHERE charger_id = #{chargerId}")
    int deleteByChargerId(@Param("chargerId") Long chargerId);

    @Select("SELECT COUNT(*) FROM charger_port WHERE charger_id = #{chargerId} AND status = 'CHARGING'")
    long countChargingByChargerId(@Param("chargerId") Long chargerId);
}

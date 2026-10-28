package com.balancloud.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserStationMapper {

    @Select("SELECT station_id FROM sys_user_station WHERE user_id = #{userId}")
    List<Long> selectStationIdsByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_station(user_id, station_id) VALUES(#{userId}, #{stationId})")
    int insert(@Param("userId") Long userId, @Param("stationId") Long stationId);

    @Delete("DELETE FROM sys_user_station WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}

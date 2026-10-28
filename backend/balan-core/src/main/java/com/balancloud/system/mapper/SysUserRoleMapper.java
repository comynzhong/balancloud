package com.balancloud.system.mapper;

import com.balancloud.system.vo.UserRoleRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectListByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT ur.user_id AS userId, ur.role_id AS roleId, r.role_name AS roleName
            FROM sys_user_role ur
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE r.del_flag = '0' AND ur.user_id = #{userId}
            ORDER BY r.role_sort
            """)
    List<UserRoleRow> selectRolesByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId}")
    int countUsersByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}

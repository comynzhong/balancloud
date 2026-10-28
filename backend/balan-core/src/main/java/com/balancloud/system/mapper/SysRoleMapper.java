package com.balancloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.system.domain.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.role_key
            FROM sys_role r
            JOIN sys_user_role ur ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = '0' AND r.del_flag = '0'
            ORDER BY r.role_sort
            """)
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 逻辑删除的角色仍占用 uk_sys_role_tenant_role_key，唯一性检查必须把它算进来。 */
    @Select("SELECT role_id FROM sys_role WHERE role_key = #{roleKey}")
    List<Long> selectRoleIdsByKeyIncludeDeleted(@Param("roleKey") String roleKey);
}

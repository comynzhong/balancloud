package com.balancloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.system.domain.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT user_id, user_name, nick_name, password, email, phone_number, avatar,
                   status, del_flag, login_ip, login_date, pwd_update_date, pwd_reset_required,
                   create_time, update_time, remark
            FROM sys_user
            WHERE user_name = #{userName} AND del_flag = '0'
            LIMIT 1
            """)
    SysUser selectByUserName(@Param("userName") String userName);

    @Select("SELECT COUNT(1) FROM sys_user WHERE user_name = #{userName}")
    int countByUserNameIgnoreDel(@Param("userName") String userName);
}

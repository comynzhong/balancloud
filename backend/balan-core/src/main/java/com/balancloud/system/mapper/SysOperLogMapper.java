package com.balancloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.system.domain.SysOperLog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysOperLogMapper extends BaseMapper<SysOperLog> {

    @Select("SELECT DISTINCT title FROM sys_oper_log WHERE title IS NOT NULL AND title <> '' ORDER BY title")
    List<String> selectDistinctTitles();
}

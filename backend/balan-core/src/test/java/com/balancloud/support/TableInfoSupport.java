package com.balancloud.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单元测试里直接构造 LambdaQueryWrapper/LambdaUpdateWrapper 时，MyBatis-Plus 需要先有实体元数据。
 */
public final class TableInfoSupport {

    private TableInfoSupport() {
    }

    public static void init(Class<?>... entityClasses) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.balancloud.support");
        for (Class<?> entityClass : entityClasses) {
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}

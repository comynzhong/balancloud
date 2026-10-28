package com.balancloud.device.ocpp.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OcppTransactionMapper extends BaseMapper<OcppTransaction> {

    /**
     * StartTransaction 的回执要在 WebSocket 线程里立刻取 transactionId，
     * 而这条路径没有租户上下文：只能跨租户按 CP identity 查。
     * 归属唯一性在握手时已经校验过，这里拿到的不会是别人的交易。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM ocpp_transaction
            WHERE device_no = #{deviceNo} AND connector_id = #{connectorId} AND status = 'ACTIVE'
            ORDER BY id DESC LIMIT 1
            """)
    OcppTransaction selectActiveAnyTenant(@Param("deviceNo") String deviceNo,
                                          @Param("connectorId") Integer connectorId);
}

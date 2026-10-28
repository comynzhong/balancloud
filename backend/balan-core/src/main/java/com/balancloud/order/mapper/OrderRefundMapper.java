package com.balancloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.order.domain.OrderRefund;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface OrderRefundMapper extends BaseMapper<OrderRefund> {

    @Select("SELECT * FROM order_refund WHERE refund_no = #{refundNo} LIMIT 1")
    OrderRefund selectByRefundNo(@Param("refundNo") String refundNo);

    /** 退款回调落终态：成功可覆盖 PROCESSING/FAILED；失败只允许 PROCESSING → FAILED，不能把成功降级。 */
    @Update("""
            UPDATE order_refund
               SET status = #{status}, refund_time = #{refundTime}
             WHERE id = #{id} AND status <> 'SUCCESS'
            """)
    int updateResult(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("refundTime") LocalDateTime refundTime);
}

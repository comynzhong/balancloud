package com.balancloud.order.vo;

import com.balancloud.order.model.RateSnapshot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailVo extends OrderVo {

    private List<OrderRateDetailVo> details;

    private RateSnapshot rateSnapshot;

    private OrderRefundVo refund;
}

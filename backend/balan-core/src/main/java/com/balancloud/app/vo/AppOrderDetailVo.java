package com.balancloud.app.vo;

import com.balancloud.order.vo.OrderDetailVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppOrderDetailVo extends OrderDetailVo {

    private AppPortVo live;

    /** 预支付调起参数（§51.5.3）：仅真实通道发起预支付后返回，客户端据此调起微信支付。 */
    private Map<String, Object> payParams;
}

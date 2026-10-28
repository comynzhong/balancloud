package com.balancloud.app.vo;

import com.balancloud.order.vo.OrderVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserDetailVo extends AppUserAdminVo {

    private List<VehicleVo> vehicles;

    private List<OrderVo> recentOrders;
}

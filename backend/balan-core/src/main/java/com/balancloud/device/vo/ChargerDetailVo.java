package com.balancloud.device.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChargerDetailVo {

    private ChargerVo charger;
    private List<PortVo> ports;
}

package com.balancloud.app.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppStationDetailVo extends AppStationVo {

    private AppRateVo rate;

    private List<AppChargerVo> devices;
}

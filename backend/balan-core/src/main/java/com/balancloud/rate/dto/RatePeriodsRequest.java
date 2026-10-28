package com.balancloud.rate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RatePeriodsRequest {

    @Valid
    @NotEmpty(message = "至少配置一个费率时段")
    private List<RatePeriodRequest> periods;
}

package com.balancloud.rate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class RateStrategyRequest {

    @NotNull(message = "请选择所属场站")
    private Long stationId;

    @NotBlank(message = "策略名称不能为空")
    @Size(max = 100, message = "策略名称不能超过 100 个字符")
    private String name;

    /** ENERGY 电价策略 / SERVICE 服务费策略，缺省按电价策略处理。 */
    private String strategyType;

    @NotBlank(message = "请选择计费模式")
    private String billingMode;

    /** 生效开始日期（含），为空表示不限。 */
    private LocalDate effectiveFrom;

    /** 生效结束日期（含），为空表示不限。 */
    private LocalDate effectiveTo;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    @Valid
    @NotEmpty(message = "至少配置一个费率时段")
    private List<RatePeriodRequest> periods;
}

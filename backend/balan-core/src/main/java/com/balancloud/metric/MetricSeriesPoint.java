package com.balancloud.metric;

/**
 * 批量测点里的一行（§60.3）：带 {@code chargerId} 与 {@code portNo}，让调用方按设备—端口分组。
 * 单设备曲线用的是 {@link MetricHistoryPointVo}，那个没有时间戳以外的维度。
 */
public record MetricSeriesPoint(long chargerId,
                                int portNo,
                                long ts,
                                Double voltage,
                                Double currentA,
                                Double temperature,
                                Double powerKw,
                                Double energyTotal) {
}

package com.balancloud.spi.dto;

import java.math.BigDecimal;

/**
 * 一条权益明细；kind 取 DISCOUNT、COUPON、POINTS 等，amount 为减免金额（元）。
 */
public record BenefitItem(String code, String name, String kind, BigDecimal amount) {
}

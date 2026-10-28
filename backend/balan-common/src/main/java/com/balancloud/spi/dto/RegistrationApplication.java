package com.balancloud.spi.dto;

public record RegistrationApplication(String tenantName,
                                      String contactName,
                                      String phone,
                                      String email,
                                      String remark) {
}

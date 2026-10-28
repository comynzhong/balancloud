package com.balancloud.framework.sensitive;

import com.balancloud.system.service.SystemConfigService;
import org.springframework.stereotype.Component;

/**
 * 手机号与车牌的展示/导出脱敏。仅改变输出，不改变库内数据。
 */
@Component
public class SensitiveDataMasker {

    private final SystemConfigService configService;

    public SensitiveDataMasker(SystemConfigService configService) {
        this.configService = configService;
    }

    public boolean displayEnabled() {
        return configService.isSensitiveMaskEnabled();
    }

    public boolean exportEnabled() {
        return configService.isSensitiveExportMasked();
    }

    public String displayPhone(String phone) {
        return displayEnabled() ? maskPhone(phone) : phone;
    }

    public String displayPlate(String plateNo) {
        return displayEnabled() ? maskPlate(plateNo) : plateNo;
    }

    public String exportPhone(String phone) {
        return exportEnabled() ? maskPhone(phone) : phone;
    }

    public String exportPlate(String plateNo) {
        return exportEnabled() ? maskPlate(plateNo) : plateNo;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        if (value.length() >= 4) {
            return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
        }
        return "***";
    }

    /**
     * 保留省份简称/字母与前两位尾号，例如 粤B12345 → 粤B***45。
     */
    public static String maskPlate(String plateNo) {
        if (plateNo == null || plateNo.isBlank()) {
            return plateNo;
        }
        String value = plateNo.trim();
        if (value.length() >= 5) {
            return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
        }
        if (value.length() >= 3) {
            return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
        }
        return "***";
    }
}

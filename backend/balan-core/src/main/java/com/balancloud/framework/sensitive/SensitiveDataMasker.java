package com.balancloud.framework.sensitive;

import com.balancloud.system.service.SystemConfigService;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 手机号与车牌的展示/导出脱敏。仅改变输出，不改变库内数据。
 */
@Component
public class SensitiveDataMasker {

    /** 文本里手写的手机号：前后不能再接数字，否则会把一串订单号里的 11 位切出来当号码。 */
    private static final Pattern PHONE_IN_TEXT = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 文本里手写的车牌。省/市简称是汉字，所以设备号、订单号这类纯 ASCII 串不会被误伤；
     * 长度放到 6 位是为了覆盖新能源的 8 位牌（省份 + 发牌字母 + 6）。
     */
    private static final Pattern PLATE_IN_TEXT =
            Pattern.compile("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-HJ-NP-Z][A-HJ-NP-Z0-9]{5,6}");

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

    /**
     * 自由文本里的手写手机号与车牌（§60.4）。
     *
     * <p>与 {@link #exportPhone} / {@link #exportPlate} 的差别不只是入参：那两个是给<b>已知是哪一列</b>的字段用的，
     * 而且受配置开关控制；工单描述与处理结论是自由文本，人会在里面手写联系方式，
     * 一旦作为语料进了向量库就删不掉（事后按 id 删要能确定哪条向量来自它），所以这里<b>不受配置开关控制、恒打码</b>。
     * 打码形状复用同两个静态函数，避免出现一处 {@code ****}、一处 {@code ***}。
     */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = PHONE_IN_TEXT.matcher(text).replaceAll(m -> maskPhone(m.group()));
        return PLATE_IN_TEXT.matcher(masked).replaceAll(m -> maskPlate(m.group()));
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

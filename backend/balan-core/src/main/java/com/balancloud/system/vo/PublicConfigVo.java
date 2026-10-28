package com.balancloud.system.vo;

import lombok.Data;

/**
 * 登录页与小程序启动时读取的公共配置，只暴露展示/开关结果，不含任何密钥。
 */
@Data
public class PublicConfigVo {

    private Brand brand;

    private boolean captchaEnabled;

    private boolean multiTenantEnabled;

    private boolean paymentEnabled;

    private String paymentMode;

    private Maintenance maintenance;

    private Miniapp miniapp;

    /** community / standard：小程序据此隐藏标准版专属入口，无需为两个版本各打一次包。 */
    private String edition;

    private Features features;

    @Data
    public static class Brand {

        private String name;

        private String subtitle;

        private String servicePhone;
    }

    @Data
    public static class Features {

        private boolean wallet;

        private boolean benefits;

        private boolean reservation;

        private boolean platform;
    }

    @Data
    public static class Maintenance {

        private boolean enabled;

        private String message;
    }

    @Data
    public static class Miniapp {

        private int defaultMaxDurationMinutes;

        private String defaultChargeLimitAmount;

        private int maxActiveOrdersPerUser;
    }
}

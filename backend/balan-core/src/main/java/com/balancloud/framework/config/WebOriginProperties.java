package com.balancloud.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 允许的浏览器来源，HTTP CORS 与 /ws/events 握手共用这一份——两处各写各的必然漏一处。
 * 浏览器对同源 POST 也会带 Origin，所以部署到 localhost 以外的地址时，必须把页面来源加进来，
 * 否则登录这类 POST 会被判 403 Invalid CORS request。
 */
@Data
@Component
@ConfigurationProperties(prefix = "balan.security")
public class WebOriginProperties {

    /** 支持 * 端口通配；公网/域名来源写在 application-local.yml 或 CORS_ALLOWED_ORIGIN_PATTERNS。 */
    private List<String> allowedOriginPatterns = List.of("http://localhost:*", "http://127.0.0.1:*");
}

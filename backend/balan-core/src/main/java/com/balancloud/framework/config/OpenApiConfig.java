package com.balancloud.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI balanCloudOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BALANCLOUD API")
                .description("衡电云控充电桩运营平台接口")
                .version("0.1.0"));
    }
}

package com.balancloud.metric;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "balan.tdengine")
public class TdengineProperties {

    private boolean enabled = true;
    private String url = "http://localhost:6041";
    private String username = "root";
    private String password;
    private String database = "balan_metrics";
}

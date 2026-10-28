package com.balancloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// 通配扫描：com.balancloud 下的 controller / mapper 自动注册
@MapperScan("com.balancloud.**.mapper")
public class BalanCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanCloudApplication.class, args);
    }
}

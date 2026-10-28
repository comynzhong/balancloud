package com.balancloud.app.service;

import com.balancloud.common.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Service
public class WechatClient {

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;

    public WechatClient(@Value("${balan.wechat.app-id:}") String appId,
                        @Value("${balan.wechat.app-secret:}") String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
    }

    public WechatSession exchange(String code) {
        if (!configured()) {
            throw new ServiceException(400, "未配置微信小程序 AppID，请使用开发登录");
        }
        try {
            SessionResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.weixin.qq.com")
                            .path("/sns/jscode2session")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(SessionResponse.class);
            if (response == null || !StringUtils.hasText(response.openid())) {
                String message = response == null || response.errmsg() == null ? "未知错误" : response.errmsg();
                throw new ServiceException(400, "微信登录失败：" + message);
            }
            return new WechatSession(response.openid(), response.unionid());
        } catch (RestClientException e) {
            throw new ServiceException(500, "微信服务暂不可用，请稍后重试");
        }
    }

    public record WechatSession(String openid, String unionid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}

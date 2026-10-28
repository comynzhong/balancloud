package com.balancloud.system.vo;

import lombok.Data;

@Data
public class SliderVerifyResult {

    private boolean verified;
    private String token;
    private boolean expired;
    private int remaining;
    private String message;

    public static SliderVerifyResult success(String token) {
        SliderVerifyResult result = new SliderVerifyResult();
        result.setVerified(true);
        result.setToken(token);
        return result;
    }

    public static SliderVerifyResult failure(String message, boolean expired, int remaining) {
        SliderVerifyResult result = new SliderVerifyResult();
        result.setMessage(message);
        result.setExpired(expired);
        result.setRemaining(remaining);
        return result;
    }
}

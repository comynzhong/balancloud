package com.balancloud.system.captcha;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.system.service.SystemConfigService;
import com.balancloud.system.vo.SliderCaptchaVo;
import com.balancloud.system.vo.SliderVerifyResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class SliderCaptchaService {

    private static final String CHALLENGE_PREFIX = "captcha:slider:challenge:";
    private static final String ATTEMPT_PREFIX = "captcha:slider:attempt:";
    private static final String PASS_PREFIX = "captcha:slider:pass:";
    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(120);
    private static final Duration PASS_TTL = Duration.ofSeconds(180);
    private static final int MAX_ATTEMPTS = 3;
    private static final int TOLERANCE = 5;

    private final SliderCaptchaGenerator generator;
    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService configService;

    public SliderCaptchaService(SliderCaptchaGenerator generator, StringRedisTemplate redisTemplate,
                                SystemConfigService configService) {
        this.generator = generator;
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    public boolean isEnabled() {
        return configService.isCaptchaEnabled();
    }

    public SliderCaptchaVo create() {
        if (!isEnabled()) {
            return SliderCaptchaVo.disabled();
        }
        SliderCaptchaGenerator.Challenge challenge = generator.generate();
        String id = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(CHALLENGE_PREFIX + id,
                String.valueOf(challenge.targetX()), CHALLENGE_TTL);
        return SliderCaptchaVo.of(id, challenge, (int) CHALLENGE_TTL.toSeconds());
    }

    public SliderVerifyResult verify(String id, int x) {
        if (!isEnabled()) {
            return SliderVerifyResult.success(null);
        }
        String challengeKey = CHALLENGE_PREFIX + id;
        String target = redisTemplate.opsForValue().get(challengeKey);
        if (!StringUtils.hasText(target)) {
            return SliderVerifyResult.failure("验证已过期，请刷新后重试", true, 0);
        }

        String attemptKey = ATTEMPT_PREFIX + id;
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptKey, CHALLENGE_TTL);
        }
        int used = attempts == null ? 1 : attempts.intValue();

        if (Math.abs(Integer.parseInt(target) - x) <= TOLERANCE) {
            redisTemplate.delete(List.of(challengeKey, attemptKey));
            String token = UUID.randomUUID().toString().replace("-", "");
            redisTemplate.opsForValue().set(PASS_PREFIX + token, "1", PASS_TTL);
            return SliderVerifyResult.success(token);
        }
        if (used >= MAX_ATTEMPTS) {
            redisTemplate.delete(List.of(challengeKey, attemptKey));
            return SliderVerifyResult.failure("验证失败次数过多，请刷新验证码", true, 0);
        }
        return SliderVerifyResult.failure("验证失败，请重试", false, MAX_ATTEMPTS - used);
    }

    /**
     * 登录入口一次性消费通行令牌，重放同一令牌会被拒绝。
     */
    public void consume(String token) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new ServiceException("请先拖动滑块完成安全验证");
        }
        Boolean removed = redisTemplate.delete(PASS_PREFIX + token);
        if (!Boolean.TRUE.equals(removed)) {
            throw new ServiceException("安全验证已失效，请重新验证");
        }
    }
}

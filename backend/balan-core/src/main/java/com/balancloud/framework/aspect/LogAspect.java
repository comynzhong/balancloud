package com.balancloud.framework.aspect;

import com.balancloud.common.annotation.Log;
import com.balancloud.common.utils.IpUtils;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.system.domain.SysOperLog;
import com.balancloud.system.mapper.SysOperLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\"(?:password|oldPassword|newPassword|confirmPassword)\"\\s*:\\s*\")[^\"]*(\")");

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    public LogAspect(SysOperLogMapper operLogMapper, ObjectMapper objectMapper) {
        this.operLogMapper = operLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(controllerLog)")
    public Object around(ProceedingJoinPoint joinPoint, Log controllerLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            saveLog(joinPoint, controllerLog, result, error, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, Log controllerLog, Object result,
                         Throwable error, long costTime) {
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setTitle(controllerLog.title());
            operLog.setBusinessType(controllerLog.businessType().ordinal());
            operLog.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "."
                    + joinPoint.getSignature().getName() + "()");
            operLog.setStatus(error == null ? 0 : 1);
            operLog.setOperName(SecurityUtils.getUsername());
            operLog.setOperTime(LocalDateTime.now());
            operLog.setCostTime(costTime);
            if (error != null) {
                operLog.setErrorMsg(truncate(error.getMessage()));
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operLog.setOperUrl(request.getRequestURI());
                operLog.setRequestMethod(request.getMethod());
                operLog.setOperIp(IpUtils.getClientIp(request));
            }

            if (controllerLog.saveRequestData()) {
                List<Object> args = Arrays.stream(joinPoint.getArgs())
                        .filter(arg -> !(arg instanceof HttpServletRequest)
                                && !(arg instanceof HttpServletResponse)
                                && !(arg instanceof MultipartFile))
                        .toList();
                operLog.setOperParam(truncate(objectMapper.writeValueAsString(args)));
            }
            if (controllerLog.saveResponseData() && result != null) {
                operLog.setJsonResult(truncate(objectMapper.writeValueAsString(result)));
            }
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("Failed to save operation audit log: {}", e.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String masked = SENSITIVE_FIELD.matcher(value).replaceAll("$1***$2");
        return masked.length() > MAX_TEXT_LENGTH ? masked.substring(0, MAX_TEXT_LENGTH) : masked;
    }
}

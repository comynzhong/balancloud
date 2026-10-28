package com.balancloud.common.exception;

import com.balancloud.common.core.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 闭源平台模块的接口前缀，社区版壳没有对应的 Controller。 */
    private static final String PLATFORM_API_PREFIX = "/api/v1/platform/";

    private final String edition;

    public GlobalExceptionHandler(@Value("${balan.edition:community}") String edition) {
        this.edition = edition;
    }

    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e) {
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public AjaxResult handleValidationException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数校验失败" : error.getDefaultMessage())
                .orElse("参数校验失败");
        return AjaxResult.error(400, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public AjaxResult handleConstraintViolation(ConstraintViolationException e) {
        return AjaxResult.error(400, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public AjaxResult handleMissingParameter(MissingServletRequestParameterException e) {
        return AjaxResult.error(400, "缺少参数：" + e.getParameterName());
    }

    /**
     * 请求体读不成对象（JSON 语法错、字段类型对不上）是调用方的问题，
     * 落到兜底 handler 会变成 500「系统异常」，前端无法提示改哪个字段。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return AjaxResult.error(400, "请求体格式错误，请检查字段类型与 JSON 结构");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return AjaxResult.error(400, "参数类型不正确：" + e.getName());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDenied(AccessDeniedException e) {
        return AjaxResult.error(403, "没有权限，请联系管理员授权");
    }

    /**
     * 未命中的路径原本会落到下面的兜底 handler，返回 500「系统异常」并打一条 ERROR 栈。
     * 社区版壳访问标准版接口就是这个情况，响应码语义要准确。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public AjaxResult handleNotFound(Exception e, HttpServletRequest request) {
        String path = request.getRequestURI();
        log.debug("接口不存在: {}", path);
        boolean platformOnly = "community".equals(edition) && path.startsWith(PLATFORM_API_PREFIX);
        return AjaxResult.error(404, platformOnly
                ? "该接口属标准版功能，当前社区版未包含"
                : "接口不存在：" + path);
    }

    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e) {
        log.error("Unhandled request exception", e);
        return AjaxResult.error("系统异常，请联系管理员");
    }
}

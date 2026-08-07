package com.hify.common.exception;

import com.hify.common.web.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一将异常转译为 {@link Result#fail(int, String)}.
 * <p>
 * Controller 不 try-catch，全部交给此类处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----------------------------------------------------
    // 业务异常
    // ----------------------------------------------------

    /**
     * BizException → 使用其 ErrorCode 返回对应错误.
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletResponse response) {
        log.warn("业务异常 code={}, message={}", e.getCode(), e.getMessage());
        // 认证相关错误 → HTTP 401，让前端能区分并跳转登录
        if (e.getCode() >= 10100 && e.getCode() <= 10199) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ----------------------------------------------------
    // 参数校验异常（@Valid / @Validated）
    // ----------------------------------------------------

    /**
     * Controller 方法参数 @Valid 校验失败.
     * 提取所有字段错误拼成一条提示.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /**
     * 缺少必填的 Query 参数.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.fail(ErrorCode.PARAM_MISSING.getCode(),
                ErrorCode.PARAM_MISSING.getMessage() + ": " + e.getParameterName());
    }

    /**
     * GET 请求 Query 参数 @Valid 校验失败.
     * <p>
     * 与 {@link #handleMethodArgumentNotValid(MethodArgumentNotValidException)} 的区别：
     * 前者处理 JSON 请求体（{@code @RequestBody + @Valid}），
     * 此方法处理 Query 参数 / Form 表单（{@code @Valid} 无 @RequestBody）。
     * </p>
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Query 参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /**
     * Service 层 {@code @Validated} 校验失败.
     * <p>
     * Controller 层校验由 {@link MethodArgumentNotValidException} / {@link BindException} 处理，
     * 此方法覆盖 Service 层方法参数校验（类上加 {@code @Validated}）的场景。
     * </p>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("Service 层校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /**
     * 请求体不可读（JSON 格式错误等）.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    // ----------------------------------------------------
    // 兜底
    // ----------------------------------------------------

    /**
     * 未预期的异常 → 返回系统内部错误，打印完整堆栈.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(),
                ErrorCode.SYSTEM_ERROR.getMessage());
    }
}

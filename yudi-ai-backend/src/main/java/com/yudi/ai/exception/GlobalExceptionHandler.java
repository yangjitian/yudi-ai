package com.yudi.ai.exception;

import com.yudi.ai.common.BaseResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用中的各种异常，返回统一的响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数验证失败异常（@RequestBody + @Valid）
     * 当使用 @Valid 注解验证 @RequestBody 参数时，验证失败会抛出此异常
     *
     * @param e 验证异常
     * @return 统一响应格式
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 获取第一个验证错误信息
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMessage = "参数验证失败";
        
        if (fieldError != null) {
            errorMessage = fieldError.getDefaultMessage();
        } else {
            // 如果没有字段错误，尝试获取全局错误
            if (!e.getBindingResult().getGlobalErrors().isEmpty()) {
                errorMessage = e.getBindingResult().getGlobalErrors().get(0).getDefaultMessage();
            }
        }
        
        log.warn("参数验证失败：{}", errorMessage);
        return BaseResponse.error(400, errorMessage);
    }

    /**
     * 处理请求参数绑定验证失败异常（@ModelAttribute + @Valid）
     * 当使用 @Valid 注解验证 @ModelAttribute 参数时，验证失败会抛出此异常
     *
     * @param e 绑定异常
     * @return 统一响应格式
     */
    @ExceptionHandler(BindException.class)
    public BaseResponse<?> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMessage = "参数验证失败";
        
        if (fieldError != null) {
            errorMessage = fieldError.getDefaultMessage();
        }
        
        log.warn("参数绑定验证失败：{}", errorMessage);
        return BaseResponse.error(400, errorMessage);
    }

    /**
     * 处理约束违反异常（@RequestParam、@PathVariable + @Valid）
     * 当使用 @Valid 注解验证 @RequestParam 或 @PathVariable 参数时，验证失败会抛出此异常
     *
     * @param e 约束违反异常
     * @return 统一响应格式
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String errorMessage = "参数验证失败";
        
        if (!violations.isEmpty()) {
            // 获取第一个验证错误信息
            errorMessage = violations.iterator().next().getMessage();
            // 或者收集所有错误信息（可选）
            // errorMessage = violations.stream()
            //         .map(ConstraintViolation::getMessage)
            //         .collect(Collectors.joining("; "));
        }
        
        log.warn("约束验证失败：{}", errorMessage);
        return BaseResponse.error(400, errorMessage);
    }

    /**
     * 处理业务异常（BusinessException）
     * 业务逻辑中抛出的 BusinessException 会被此方法捕获
     * 注意：此方法需要在 handleRuntimeException 之前，因为 BusinessException 继承自 RuntimeException
     *
     * @param e 业务异常
     * @return 统一响应格式
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return BaseResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理业务异常（RuntimeException）
     * 业务逻辑中抛出的其他 RuntimeException 会被此方法捕获
     *
     * @param e 运行时异常
     * @return 统一响应格式
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> handleRuntimeException(RuntimeException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        return BaseResponse.error(400, e.getMessage());
    }

    /**
     * 处理其他所有异常
     * 作为兜底处理，捕获所有未被上述方法处理的异常
     *
     * @param e 异常
     * @return 统一响应格式
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        return BaseResponse.error(500, "系统异常，请稍后重试");
    }


}


package com.yudi.ai.common;

import lombok.Getter;

/**
 * 错误码枚举
 * 统一管理系统中所有的错误码和错误信息
 */
@Getter
public enum ErrorCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 参数错误
     */
    PARAMETER_ERROR(400, "参数错误"),
    PARAMETER_NULL(400, "参数为空"),
    PARAMETER_INVALID(400, "参数无效"),

    /**
     * 未授权
     */
    NOT_AUTHORIZED(401, "未授权"),
    NOT_LOGIN(401, "未登录"),
    TOKEN_INVALID(401, "令牌无效"),
    TOKEN_EXPIRED(401, "令牌已过期"),

    /**
     * 禁止访问
     */
    FORBIDDEN(403, "禁止访问"),
    NO_PERMISSION(403, "无权限"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),
    USER_NOT_FOUND(404, "用户不存在"),

    /**
     * 业务异常
     */
    BUSINESS_ERROR(400, "业务处理失败"),
    OPERATION_FAILED(400, "操作失败"),

    /**
     * 用户相关错误
     */
    USER_ALREADY_EXISTS(400, "用户已存在"),
    USER_ACCOUNT_EXISTS(400, "该邮箱已被注册"),
    USER_DISABLED(400, "用户已被禁用，无法登录"),
    USER_PASSWORD_ERROR(400, "密码错误"),

    /**
     * 验证码相关错误
     */
    VERIFICATION_CODE_ERROR(400, "验证码错误"),
    VERIFICATION_CODE_EXPIRED(400, "验证码已过期，请重新获取"),
    VERIFICATION_CODE_NOT_FOUND(400, "验证码不存在"),

    /**
     * 系统错误
     */
    SYSTEM_ERROR(500, "系统内部错误"),
    DATABASE_ERROR(500, "数据库操作失败"),
    NETWORK_ERROR(500, "网络错误"),
    SERVICE_UNAVAILABLE(500, "服务不可用");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}



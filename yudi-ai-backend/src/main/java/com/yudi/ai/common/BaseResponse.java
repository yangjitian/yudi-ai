package com.yudi.ai.common;

import lombok.Data;

/**
 * 通用响应类
 */
@Data
public class BaseResponse<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    public BaseResponse() {
    }

    public BaseResponse(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(200, "操作成功", data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> BaseResponse<T> success() {
        return new BaseResponse<>(200, "操作成功", null);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> BaseResponse<T> success(String message, T data) {
        return new BaseResponse<>(200, message, data);
    }

    /**
     * 失败响应
     */
    public static <T> BaseResponse<T> error(String message) {
        return new BaseResponse<>(500, message, null);
    }

    /**
     * 失败响应（自定义状态码）
     */
    public static <T> BaseResponse<T> error(Integer code, String message) {
        return new BaseResponse<>(code, message, null);
    }
}


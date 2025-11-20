package com.yudi.ai.model.enmus;

import lombok.Getter;

/**
 * 邮箱验证码类型枚举
 * 
 * @author yudi
 */
@Getter
public enum VerificationType {
    REGISTER("注册", "【小小雨滴】注册验证码"),
    LOGIN("登录", "【小小雨滴】登录验证码"),
    CHANGE_ACCOUNT("换绑邮箱", "【小小雨滴】邮箱换绑验证码");

    private final String desc;
    private final String subject;

    VerificationType(String desc, String subject) {
        this.desc = desc;
        this.subject = subject;
    }
}

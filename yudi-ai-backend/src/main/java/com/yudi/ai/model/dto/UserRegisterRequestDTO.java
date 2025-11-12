package com.yudi.ai.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求DTO
 */
@Data
public class UserRegisterRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 306092697074984917L;
    /**
     * 用户账号（邮箱）
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String userAccount;
    
    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码必须是6位数字")
    private String verificationCode;
}



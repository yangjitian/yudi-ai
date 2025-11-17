package com.yudi.ai.model.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户资料修改请求 DTO
 */
@Data
public class UserUpdateRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = -42193847562347981L;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户账号（邮箱）
     */
    @Email(message = "邮箱格式不正确")
    private String userAccount;

    /**
     * 用户头像 URL
     */
    private String userAvatar;
}



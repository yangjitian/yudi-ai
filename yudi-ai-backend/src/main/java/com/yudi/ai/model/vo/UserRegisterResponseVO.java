package com.yudi.ai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 用户注册响应VO
 */
@Data
public class UserRegisterResponseVO {
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户账号（邮箱）
     */
    private String userAccount;
    
    /**
     * 用户昵称
     */
    private String userName;
    
    /**
     * 用户头像URL
     */
    private String userAvatar;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

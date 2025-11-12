package com.yudi.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.ai.model.dto.LoginRequestDTO;
import com.yudi.ai.model.vo.LoginResponseVO;
import com.yudi.ai.model.dto.UserRegisterRequestDTO;
import com.yudi.ai.model.entity.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * 
     * @param userRegisterRequestDTO 注册请求
     * @return 注册成功后的用户ID
     */
    Long userRegister(UserRegisterRequestDTO userRegisterRequestDTO);

    /**
     * 用户登录
     * 
     * @param loginRequestDTO 登录请求
     * @return 登录结果
     */
    LoginResponseVO userLogin(LoginRequestDTO loginRequestDTO);

    /**
     * 用户登出
     * @param token 登录凭证
     * @return 登出结果
     */
    boolean userLogout(String token);
}

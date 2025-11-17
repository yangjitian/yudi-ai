package com.yudi.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.model.dto.LoginRequestDTO;
import com.yudi.ai.model.vo.LoginResponseVO;
import com.yudi.ai.model.dto.UserRegisterRequestDTO;
import com.yudi.ai.model.dto.UserUpdateRequestDTO;
import com.yudi.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     * 
     * @param userRegisterRequestDTO 注册请求
     * @return 注册成功后的用户ID
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@Valid @RequestBody UserRegisterRequestDTO userRegisterRequestDTO) {
        Long userId = userService.userRegister(userRegisterRequestDTO);
        return BaseResponse.success("注册成功", userId);
    }

    /**
     * 用户登录
     * 
     * @param loginRequestDTO 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public BaseResponse<LoginResponseVO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        LoginResponseVO responseVO = userService.userLogin(loginRequestDTO);
        return BaseResponse.success("登录成功", responseVO);
    }

    /**
     * 用户登出
     * 登出时会销毁Token，使Token失效
     *
     * @param authHeader 登录凭证（从请求头获取，可能包含Bearer前缀）
     * @return 登出结果
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(@RequestHeader("Authorization") String authHeader) {
        // 去掉Bearer前缀，获取实际的token
        String token = authHeader;
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        boolean result = userService.userLogout(token);
        return BaseResponse.success("登出成功", result);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    @GetMapping("/current")
    public BaseResponse<LoginResponseVO> getCurrentUser() {
        LoginResponseVO loginResponseVO = userService.getCurrentUser();
        return BaseResponse.success(loginResponseVO);
    }

    /**
     * 修改当前登录用户资料
     *
     * @param userUpdateRequestDTO 修改请求
     * @param authHeader           授权头，用于获取当前 token
     * @return 最新用户信息
     */
    @PostMapping("/update")
    public BaseResponse<LoginResponseVO> updateCurrentUser(@Valid @RequestBody UserUpdateRequestDTO userUpdateRequestDTO,
                                                           @RequestHeader("Authorization") String authHeader) {
        String token = authHeader;
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        LoginResponseVO responseVO = userService.updateCurrentUser(userUpdateRequestDTO, token);
        return BaseResponse.success("更新成功", responseVO);
    }
}
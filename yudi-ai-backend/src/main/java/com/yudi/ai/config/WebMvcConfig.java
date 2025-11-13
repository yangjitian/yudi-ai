package com.yudi.ai.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

import static com.yudi.ai.service.impl.UserServiceImpl.LOGIN_TOKEN_KEY_PREFIX;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(@NotNull HttpServletRequest request
                            , @NotNull HttpServletResponse response
                            , @NotNull Object handler) {
                        // 1.获取token
                        String token = request.getHeader("Authorization");
                        if (StrUtil.isBlank(token)) {
                            //不存在，拦截
                            response.setStatus(401);
                            return false;
                        }
                        // 2.基于token获取redis中的用户
                        String key = LOGIN_TOKEN_KEY_PREFIX + token;
                        String userJson = stringRedisTemplate.opsForValue().get(key);
                        // 3.判断用户是否存在
                        if (StrUtil.isBlank(userJson)) {
                            //不存在，拦截
                            response.setStatus(401);
                            return false;
                        }
                        // 4.将查询到的hash数据转为User对象
                        User user = JSONUtil.toBean(userJson, User.class);
                        // 5.存在，保存用户信息到ThreadLocal
                        UserHolder.saveUser(user);
                        // 6.刷新token有效期
                        stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
                        // 7.放行
                        return true;
                    }

                    @Override
                    public void afterCompletion(@NotNull HttpServletRequest request
                            , @NotNull HttpServletResponse response
                            , @NotNull Object handler
                            , Exception exception) {
                        // 移除用户，避免内存泄露
                        UserHolder.removeUser();
                    }
                }).addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/email/send-register-code",
                        "/email/send-login-code",
                        "/cook/pg/chat",
                        "/cook/pg/chat/stream",
                        "/yd_manus/chat",
                        "/yd_manus/chat/stream"
                );
    }
}


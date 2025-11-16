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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

import static com.yudi.ai.service.impl.UserServiceImpl.LOGIN_TOKEN_KEY_PREFIX;

@Slf4j
@EnableAsync
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
                        String requestPath = request.getRequestURI();
                        log.debug("拦截器处理请求: {}, Handler: {}", requestPath, handler.getClass().getSimpleName());
                        
                        // 1.获取token
                        String authHeader = request.getHeader("Authorization");
                        if (StrUtil.isBlank(authHeader)) {
                            //不存在，拦截
                            log.warn("请求缺少Authorization header: {}", requestPath);
                            response.setStatus(401);
                            return false;
                        }
                        // 2.去掉Bearer前缀，获取实际的token
                        String token = authHeader;
                        if (authHeader.startsWith("Bearer ")) {
                            token = authHeader.substring(7);
                        }
                        if (StrUtil.isBlank(token)) {
                            log.warn("请求token为空: {}", requestPath);
                            response.setStatus(401);
                            return false;
                        }
                        // 3.基于token获取redis中的用户
                        String key = LOGIN_TOKEN_KEY_PREFIX + token;
                        String userJson = stringRedisTemplate.opsForValue().get(key);
                        // 4.判断用户是否存在
                        if (StrUtil.isBlank(userJson)) {
                            //不存在，拦截
                            log.warn("token无效或已过期: {}", requestPath);
                            response.setStatus(401);
                            return false;
                        }
                        // 5.将查询到的hash数据转为User对象
                        User user = JSONUtil.toBean(userJson, User.class);
                        // 6.存在，保存用户信息到ThreadLocal
                        UserHolder.saveUser(user);
                        // 7.刷新token有效期
                        stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
                        // 8.放行
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
                        "/health",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/doc.html"
                );
    }
}


package com.yudi.ai.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.networknt.org.apache.commons.validator.routines.EmailValidator;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.model.dto.EmailRequest;
import com.yudi.ai.model.enmus.VerificationType;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.service.EmailService;
import com.yudi.ai.service.UserService;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/email")
public class EmailController {

    /**
     * 注册验证码Redis key前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification:code:";
    
    /**
     * 登录验证码Redis key前缀
     */
    private static final String LOGIN_CODE_KEY_PREFIX = "login:code:";

    /**
     * 换绑邮箱验证码 Redis key 前缀
     */
    private static final String CHANGE_EMAIL_CODE_KEY_PREFIX = "change_email:code:";
    /**
     * 换绑邮箱验证码校验通过标识 Redis key 前缀
     */
    private static final String CHANGE_EMAIL_VERIFIED_FLAG_PREFIX = "change_email:verified:";

    @Resource
    private EmailService emailService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @PostMapping("/send-register-code")
    public BaseResponse<Boolean> sendRegisterCode(@RequestBody EmailRequest emailRequest) {
        sendEmailVerification(emailRequest.getEmail(), VerificationType.REGISTER);
        return BaseResponse.success(true);
    }

    @PostMapping("/send-login-code")
    public BaseResponse<Boolean> sendLoginCode(@RequestBody EmailRequest emailRequest) {
        sendEmailVerification(emailRequest.getEmail(), VerificationType.LOGIN);
        return BaseResponse.success(true);
    }

    /**
     * 发送换绑邮箱验证码（发送到当前登录账号绑定的邮箱）
     */
    @PostMapping("/send-change-account-code")
    public BaseResponse<Boolean> sendChangeAccountCode() {
        User currentUser = UserHolder.getUser();
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN, "用户未登录");
        String email = currentUser.getUserAccount();
        ThrowUtils.throwIf(StrUtil.isBlank(email), ErrorCode.PARAMETER_ERROR, "当前账号未绑定邮箱");
        sendEmailVerification(email, VerificationType.CHANGE_ACCOUNT);
        return BaseResponse.success(true);
    }

    /**
     * 校验换绑邮箱验证码（仅校验当前登录账号）
     */
    @PostMapping("/verify-change-account-code")
    public BaseResponse<Boolean> verifyChangeAccountCode(@RequestBody EmailRequest emailRequest) {
        String code = emailRequest.getCode();
        ThrowUtils.throwIf(StrUtil.isBlank(code), ErrorCode.PARAMETER_NULL, "验证码不能为空");

        User currentUser = UserHolder.getUser();
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN, "用户未登录");

        String email = currentUser.getUserAccount();
        ThrowUtils.throwIf(StrUtil.isBlank(email), ErrorCode.PARAMETER_ERROR, "当前账号未绑定邮箱");

        String redisKey = CHANGE_EMAIL_CODE_KEY_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        ThrowUtils.throwIf(StrUtil.isBlank(storedCode), ErrorCode.VERIFICATION_CODE_EXPIRED, "验证码已过期");
        ThrowUtils.throwIf(!StrUtil.equals(storedCode, code), ErrorCode.VERIFICATION_CODE_ERROR, "验证码错误");

        // 校验通过后，删除验证码
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.opsForValue().set(
                CHANGE_EMAIL_VERIFIED_FLAG_PREFIX + currentUser.getId(),
                "1",
                5,
                TimeUnit.MINUTES
        );
        return BaseResponse.success(true);
    }

    private void sendEmailVerification(String email, VerificationType type) {
        ThrowUtils.throwIf(email == null, ErrorCode.PARAMETER_ERROR, "邮箱不能为空");
        ThrowUtils.throwIf(!EmailValidator.getInstance().isValid(email), ErrorCode.PARAMETER_ERROR, "无效的邮箱地址");

        // 1. 校验邮箱状态
        User user = userService.lambdaQuery().eq(User::getUserAccount, email).one();
        switch (type) {
            case REGISTER:
                ThrowUtils.throwIf(user != null, ErrorCode.PARAMETER_ERROR, "该邮箱已被注册");
                break;
            case LOGIN:
                ThrowUtils.throwIf(user == null, ErrorCode.PARAMETER_ERROR, "该邮箱未注册");
                break;
            case CHANGE_ACCOUNT:
                // 换绑场景下，必须是已注册邮箱
                ThrowUtils.throwIf(user == null, ErrorCode.PARAMETER_ERROR, "该邮箱未注册");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }

        // 2. 生成验证码
        String code = String.valueOf(RandomUtil.randomInt(100001, 1000000));
        log.info("生成验证码 [{}]：{}", type.name(), code);

        // 3. 存储 Redis（根据类型使用不同的key前缀）
        String redisKey = switch (type) {
            case REGISTER -> VERIFICATION_CODE_KEY_PREFIX + email;
            case LOGIN -> LOGIN_CODE_KEY_PREFIX + email;
            case CHANGE_ACCOUNT -> CHANGE_EMAIL_CODE_KEY_PREFIX + email;
        };

        try {
            stringRedisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis 存储验证码失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        // 4. 构造邮件内容并发送
        String subject = type.getSubject();
        String content = String.format("您好，您的%s验证码是：%s，有效期为5分钟，请勿泄露给他人。", type.getDesc(), code);
        emailService.sendEmail(email, subject, content);
        log.info("验证码邮件 [{}] 已发送至 {}", type.name(), email);
    }
}

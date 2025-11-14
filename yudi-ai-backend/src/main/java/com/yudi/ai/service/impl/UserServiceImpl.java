package com.yudi.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.mapper.UserMapper;
import com.yudi.ai.model.dto.LoginRequestDTO;
import com.yudi.ai.model.vo.LoginResponseVO;
import com.yudi.ai.model.dto.UserRegisterRequestDTO;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.service.UserService;
import com.yudi.ai.utils.UserHolder;
import com.yudi.ai.utils.UsernameGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 注册验证码Redis key前缀
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification:code:";
    
    // 登录验证码Redis key前缀
    private static final String LOGIN_CODE_KEY_PREFIX = "login:code:";

    // 用户登录token Redis key前缀
    public static final String LOGIN_TOKEN_KEY_PREFIX = "user:login:token:";



    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long userRegister(UserRegisterRequestDTO userRegisterRequestDTO) {
        String userAccount = userRegisterRequestDTO.getUserAccount();
        String verificationCode = userRegisterRequestDTO.getVerificationCode();
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount,verificationCode),ErrorCode.PARAMETER_NULL,"参数不能为空");
        // 1. 验证验证码
        String storedCode = stringRedisTemplate.opsForValue().get(VERIFICATION_CODE_KEY_PREFIX + userAccount);
        if (StrUtil.isBlank(storedCode) || !storedCode.equals(verificationCode)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "验证码错误或已过期");
        }

        // 2. 检查邮箱是否已被注册
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        long count = this.count(queryWrapper);
        ThrowUtils.throwIf(count > 0,ErrorCode.USER_ACCOUNT_EXISTS,"账户已存在");

        // 4. 创建用户
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserName(UsernameGenerator.generateUniqueUsername());
        user.setStatus(1);

        boolean saveSuccess = this.save(user);
        ThrowUtils.throwIf(!saveSuccess,ErrorCode.OPERATION_FAILED,"用户注册失败");

        // 4. 删除验证码（注册成功后清除）
        stringRedisTemplate.delete(VERIFICATION_CODE_KEY_PREFIX + userAccount);
        log.info("用户注册成功，邮箱：{}，用户ID：{}", userAccount, user.getId());

        // 5. 返回用户ID
        return user.getId();
    }

    @Override
    public LoginResponseVO userLogin(LoginRequestDTO loginRequestDTO) {
        String userAccount = loginRequestDTO.getUserAccount();
        String verificationCode = loginRequestDTO.getVerificationCode();

        // 1. 验证验证码
        String key = LOGIN_CODE_KEY_PREFIX + userAccount;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        ThrowUtils.throwIf(storedCode == null,ErrorCode.VERIFICATION_CODE_EXPIRED);
        ThrowUtils.throwIf(!storedCode.equals(verificationCode),ErrorCode.VERIFICATION_CODE_ERROR);

        // 2. 查询用户信息
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        User user = this.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null,ErrorCode.USER_NOT_FOUND);
        
        // 3. 检查用户状态
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 4. 删除验证码（登录成功后清除）
        stringRedisTemplate.delete(key);
        log.info("用户登录成功，邮箱：{}，用户ID：{}", userAccount, user.getId());

        // 5. 生成Token并存入Redis
        String token = UUID.randomUUID().toString();
        String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
        stringRedisTemplate.opsForValue().set(tokenKey, JSONUtil.toJsonStr(user), 30, TimeUnit.MINUTES);

        // 6. 构建响应对象
        LoginResponseVO responseVO = new LoginResponseVO();
        responseVO.setId(user.getId());
        responseVO.setUserAccount(user.getUserAccount());
        responseVO.setUserName(user.getUserName());
        responseVO.setUserAvatar(user.getUserAvatar());
        responseVO.setToken(token);

        return responseVO;
    }

    @Override
    public boolean userLogout(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
        return Boolean.TRUE.equals(stringRedisTemplate.delete(tokenKey));
    }

    @Override
    public LoginResponseVO getCurrentUser() {
        // 从ThreadLocal中获取当前用户信息
        User user = UserHolder.getUser();
        ThrowUtils.throwIf(user == null,ErrorCode.NOT_LOGIN,"用户未登录");

        // 构建响应对象
        LoginResponseVO responseVO = new LoginResponseVO();
        responseVO.setId(user.getId());
        responseVO.setUserAccount(user.getUserAccount());
        responseVO.setUserName(user.getUserName());
        responseVO.setUserAvatar(user.getUserAvatar());
        return responseVO;
    }
}
package com.yudi.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.mapper.UserMapper;
import com.yudi.ai.model.dto.LoginRequestDTO;
import com.yudi.ai.model.vo.LoginResponseVO;
import com.yudi.ai.model.dto.UserRegisterRequestDTO;
import com.yudi.ai.model.dto.UserUpdateRequestDTO;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.service.UserService;
import com.yudi.ai.utils.UserHolder;
import com.yudi.ai.utils.UsernameGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
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
    // 换绑校验通过标识 key 前缀
    private static final String CHANGE_EMAIL_VERIFIED_FLAG_PREFIX = "change_email:verified:";
    private static final long MAX_AVATAR_SIZE = 4L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_SUFFIX = CollUtil.newHashSet("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final String PROJECT_NAME = "yudiAi";

    @Value("${cos.client.host:}")
    private String cosHost;
    @Value("${cos.client.secretId:}")
    private String cosSecretId;
    @Value("${cos.client.secretKey:}")
    private String cosSecretKey;
    @Value("${cos.client.region:}")
    private String cosRegion;
    @Value("${cos.client.bucketName:}")
    private String cosBucketName;

    private COSClient cosClient;

    @PostConstruct
    public void initCosClient() {
        if (StrUtil.hasBlank(cosHost, cosSecretId, cosSecretKey, cosRegion, cosBucketName)) {
            log.warn("COS 配置不完整，头像上传功能将不可用");
            return;
        }
        COSCredentials credentials = new BasicCOSCredentials(cosSecretId, cosSecretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(cosRegion));
        this.cosClient = new COSClient(credentials, clientConfig);
        log.info("COS 客户端初始化完成，bucketName={}", cosBucketName);
    }

    @PreDestroy
    public void destroyCosClient() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseVO updateCurrentUser(UserUpdateRequestDTO userUpdateRequestDTO, String token) {
        // 1. 获取当前登录用户
        User currentUser = UserHolder.getUser();
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN, "用户未登录");

        boolean needUpdate = false;
        boolean emailUpdated = false;

        // 2. 更新昵称
        if (StrUtil.isNotBlank(userUpdateRequestDTO.getUserName())) {
            String newName = StrUtil.trim(userUpdateRequestDTO.getUserName());
            if (!StrUtil.equals(newName, currentUser.getUserName())) {
                currentUser.setUserName(newName);
                needUpdate = true;
            }
        }

        // 3. 更新邮箱（userAccount），需要校验唯一性
        if (StrUtil.isNotBlank(userUpdateRequestDTO.getUserAccount())) {
            String newAccount = StrUtil.trim(userUpdateRequestDTO.getUserAccount());
            if (!StrUtil.equals(newAccount, currentUser.getUserAccount())) {
                String verifiedKey = CHANGE_EMAIL_VERIFIED_FLAG_PREFIX + currentUser.getId();
                String verifiedFlag = stringRedisTemplate.opsForValue().get(verifiedKey);
                ThrowUtils.throwIf(StrUtil.isBlank(verifiedFlag), ErrorCode.VERIFICATION_CODE_NOT_FOUND, "请先完成邮箱换绑验证");
                long count = this.lambdaQuery()
                        .eq(User::getUserAccount, newAccount)
                        .ne(User::getId, currentUser.getId())
                        .count();
                ThrowUtils.throwIf(count > 0, ErrorCode.USER_ACCOUNT_EXISTS, "该邮箱已被其他账号使用");
                currentUser.setUserAccount(newAccount);
                needUpdate = true;
                emailUpdated = true;
            }
        }

        // 4. 更新头像
        if (StrUtil.isNotBlank(userUpdateRequestDTO.getUserAvatar())) {
            String newAvatar = StrUtil.trim(userUpdateRequestDTO.getUserAvatar());
            if (!StrUtil.equals(newAvatar, currentUser.getUserAvatar())) {
                currentUser.setUserAvatar(newAvatar);
                needUpdate = true;
            }
        }

        ThrowUtils.throwIf(!needUpdate, ErrorCode.PARAMETER_ERROR, "没有需要更新的内容");

        // 5. 持久化到数据库
        boolean updated = this.updateById(currentUser);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_FAILED, "更新用户信息失败");

        // 6. 同步更新 Redis 中的用户信息，保持 Token 对应的数据最新
        if (StrUtil.isNotBlank(token)) {
            String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
            stringRedisTemplate.opsForValue().set(tokenKey, JSONUtil.toJsonStr(currentUser), 30, TimeUnit.MINUTES);
        }

        // 7. 构建返回对象
        LoginResponseVO responseVO = new LoginResponseVO();
        responseVO.setId(currentUser.getId());
        responseVO.setUserAccount(currentUser.getUserAccount());
        responseVO.setUserName(currentUser.getUserName());
        responseVO.setUserAvatar(currentUser.getUserAvatar());
        responseVO.setToken(token);

        if (emailUpdated) {
            stringRedisTemplate.delete(CHANGE_EMAIL_VERIFIED_FLAG_PREFIX + currentUser.getId());
        }

        return responseVO;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMETER_NULL, "请上传头像文件");
        ThrowUtils.throwIf(file.getSize() > MAX_AVATAR_SIZE, ErrorCode.PARAMETER_ERROR, "头像不能超过4MB");
        ThrowUtils.throwIf(cosClient == null, ErrorCode.SYSTEM_ERROR, "存储服务未初始化");

        User currentUser = UserHolder.getUser();
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN, "用户未登录");

        String originalFilename = file.getOriginalFilename();
        String suffix = StrUtil.blankToDefault(StrUtil.subAfter(originalFilename, ".", true), "").toLowerCase();
        ThrowUtils.throwIf(!ALLOWED_AVATAR_SUFFIX.contains(suffix), ErrorCode.PARAMETER_ERROR, "仅支持常见的图片格式（jpg/png/jpeg/gif/bmp/webp）");

        String datePath = DateUtil.format(DateUtil.date(), "yyyy/MM/dd");
        String objectKey = StrUtil.format("{}/avatar/{}/{}/{}.{}",
                PROJECT_NAME,
                currentUser.getId(),
                datePath,
                IdUtil.fastSimpleUUID(),
                suffix);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"));
        metadata.addUserMetadata("project", PROJECT_NAME);

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(cosBucketName, objectKey, inputStream, metadata);
            cosClient.putObject(request);
        } catch (IOException | CosClientException e) {
            log.error("用户头像上传失败，userId={}, fileName={}", currentUser.getId(), originalFilename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败，请稍后重试");
        }

        ThrowUtils.throwIf(StrUtil.isBlank(cosHost), ErrorCode.SYSTEM_ERROR, "存储域名未配置");
        String cleanHost = StrUtil.removeSuffix(cosHost, "/");
        return cleanHost + "/" + objectKey;
    }
}
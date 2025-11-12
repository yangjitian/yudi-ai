package com.yudi.ai.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.IdUtil;

/**
 * 用户名生成工具类
 *
 * @author yudi
 */
public class UsernameGenerator {

    /** 固定前缀 */
    private static final String PREFIX = "小yu滴_";

    /** 字符池（小写字母+数字） */
    private static final String CHAR_POOL = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成唯一用户名：小yu滴_ + 6位字母数字组合（随机 + 唯一保证）
     *
     * @return 唯一用户名
     */
    public static String generateUniqueUsername() {
        // 生成随机 6 位字符串
        String randomSuffix = RandomUtil.randomString(CHAR_POOL, 6);

        // 附加一个简短唯一标识（避免分布式冲突，可选）
        String shortUUID = IdUtil.fastSimpleUUID().substring(0, 4);

        return PREFIX + randomSuffix + shortUUID;
    }
}

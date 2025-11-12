package com.yudi.ai.utils;

import com.yudi.ai.model.entity.User;

/**
 * 用户信息上下文
 */
public class UserHolder {

    private static final ThreadLocal<User> tl = new ThreadLocal<>();

    public static void saveUser(User user){
        tl.set(user);
    }

    public static User getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}

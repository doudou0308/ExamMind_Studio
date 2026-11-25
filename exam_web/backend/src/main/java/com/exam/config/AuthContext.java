package com.exam.config;

import com.exam.model.User;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证上下文管理器
 * <p>
 * 两大核心职责：
 * 1. Token 管理：负责 token 的创建、查询和删除，模拟一个简易的"登录会话"机制
 * 2. 当前用户存储：利用 ThreadLocal 在当前请求线程中暂存登录用户信息，
 *    方便 Controller/Service 随时获取"当前是谁在操作"
 */
public class AuthContext {

    /**
     * Token → 用户映射表
     * 使用 ConcurrentHashMap 保证并发安全
     */
    private static final Map<String, User> tokenStore = new ConcurrentHashMap<>();

    /**
     * 当前请求线程绑定的用户信息
     * ThreadLocal 确保每个请求线程有自己独立的 User 副本，互不干扰
     */
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    /**
     * 为用户创建一个登录 token（相当于"签发一张通行证"）
     *
     * @param user 登录成功的用户对象
     * @return 生成的唯一 token 字符串（UUID 格式）
     */
    public static String createToken(User user) {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, user);
        return token;
    }

    /**
     * 根据 token 查询对应的用户（验证"通行证是否有效"）
     *
     * @param token 请求头中携带的 token
     * @return 对应的用户对象，token 无效时返回 null
     */
    public static User getUserByToken(String token) {
        return tokenStore.get(token);
    }

    /**
     * 将用户绑定到当前请求线程（拦截器校验通过后调用）
     */
    public static void setCurrentUser(User user) {
        currentUser.set(user);
    }

    /**
     * 获取当前请求线程绑定的用户（业务代码中随时获取"当前操作者"）
     *
     * @return 当前登录用户，未登录时返回 null
     */
    public static User getCurrentUser() {
        return currentUser.get();
    }

    /**
     * 清理当前线程的用户信息（请求结束后调用，防止内存泄漏）
     */
    public static void clear() {
        currentUser.remove();
    }
}

package com.exam.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置类
 * <p>
 * 核心职责：注册认证拦截器，规定哪些接口需要登录校验、哪些可以匿名访问。
 * 相当于"门禁系统"的配置中心——告诉系统哪些门要刷卡、哪些门可以自由进出。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    /**
     * 注册拦截器规则，指定哪些接口需要登录校验、哪些可以匿名访问
     *
     * @param registry Spring 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 对所有 /api/** 路径的请求进行拦截（需要登录）
                .addPathPatterns("/api/**")
                // 登录接口放行
                .excludePathPatterns("/api/user/login");
    }
}

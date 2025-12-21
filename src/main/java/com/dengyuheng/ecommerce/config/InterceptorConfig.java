package com.dengyuheng.ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(InterceptorConfig.class);
    
    @Autowired
    private JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        logger.info("注册JWT拦截器");
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/api/**") // 拦截所有/api路径
                .excludePathPatterns("/api/system/auth/login") // 排除登录接口
                .excludePathPatterns("/api/system/auth/initSuperAdmin"); // 排除初始化超级管理员接口
    }
}
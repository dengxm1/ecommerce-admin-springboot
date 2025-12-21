package com.dengyuheng.ecommerce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.dengyuheng.ecommerce.annotation.PassToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // 放行OPTIONS请求
        if (HttpMethod.OPTIONS.toString().equals(request.getMethod())) {
            return true;
        }
        
        // 检查是否有@PassToken注解
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            
            // 检查方法上的注解
            PassToken methodPassToken = handlerMethod.getMethod().getAnnotation(PassToken.class);
            // 检查类上的注解
            PassToken classPassToken = handlerMethod.getBeanType().getAnnotation(PassToken.class);
            
            // 如果方法或类上有@PassToken注解，则放行
            if (methodPassToken != null || classPassToken != null) {
                return true;
            }
        }
        
        // 从请求头中获取token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"缺少token\"}");
            return false;
        }
        
        try {
            // 验证token并获取信息
            String userId = jwtTokenUtil.getUserIdFromToken(token);
            String username = jwtTokenUtil.getUsernameFromToken(token);
            // 可以将用户信息存入request中，方便后续使用
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            return true;
        } catch (Exception e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"token无效或已过期\"}");
            return false;
        }
    }
}
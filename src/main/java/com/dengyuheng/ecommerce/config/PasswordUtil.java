package com.dengyuheng.ecommerce.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtil {
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
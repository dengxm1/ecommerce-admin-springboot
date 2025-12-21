package com.dengyuheng.ecommerce.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenUtil {
    
    @Value("${jwt.secret}")
    private String secretString;
    
    @Value("${jwt.expiration:604800000}")  // 默认值
    private Long expiration;
    
    @Value("${jwt.issuer:my-personal-app}")  // 默认值
    private String issuer;
    
    private SecretKey secretKey;
    private JwtParser jwtParser;
    
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes());
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }
    
    public String generateToken(String userId, String username) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

      
    /**
     * 获取token过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaims(token).getExpiration();
    }
    
    
    public String getUsernameFromToken(String token) {
        return getClaims(token).get("username", String.class);
    }
    
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
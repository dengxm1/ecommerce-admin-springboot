package com.dengyuheng.ecommerce.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SystemUserRole {
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
    
}

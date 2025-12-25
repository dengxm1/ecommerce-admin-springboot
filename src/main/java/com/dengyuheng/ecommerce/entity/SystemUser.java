package com.dengyuheng.ecommerce.entity;

import java.time.LocalDateTime;

import lombok.Data;


@Data
public class SystemUser {
    private Long id;
    private Long tenantId;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer isEnabled;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
}   

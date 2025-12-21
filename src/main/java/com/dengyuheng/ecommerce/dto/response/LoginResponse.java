package com.dengyuheng.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String message;
    private Integer code = 200;

    public LoginResponse(String message,String token) {
        this.token = token;
        this.message = message;
    }
}
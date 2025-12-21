package com.dengyuheng.ecommerce.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T>{
    private String message;
    private Integer code=200;
    private T data;  // 使用泛型

       // 成功响应 - 无数据
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("操作成功", 200, null);
    }
    
    // 成功响应 - 有数据
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("操作成功", 200, data);
    }
    
    // 成功响应 - 自定义消息
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, 200, data);
    }
    
    // 错误响应 - 默认500
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(message, 500, null);
    }
    
    // 错误响应 - 自定义状态码
    public static <T> ApiResponse<T> error(String message, Integer code) {
        return new ApiResponse<>(message, code, null);
    }
       // 构造函数
    public ApiResponse(String message) {
        this(message, 200, null);
    }
    
    public ApiResponse(String message, T data) {
        this(message, 200, data);
    }
}

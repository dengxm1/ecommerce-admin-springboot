package com.dengyuheng.ecommerce.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dengyuheng.ecommerce.config.JwtTokenUtil;
import com.dengyuheng.ecommerce.dto.common.ApiResponse;
import com.dengyuheng.ecommerce.dto.response.LoginResponse;
import com.dengyuheng.ecommerce.entity.SystemMenu;
import com.dengyuheng.ecommerce.entity.SystemUser;
import com.dengyuheng.ecommerce.service.SystemUserService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;




@RestController
@RequestMapping("/api/system/auth")
public class SystemUserController {

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @PostMapping("/initSuperAdmin")
    public ResponseEntity<?> initSuperAdmin(){
        try{
            SystemUser user = new SystemUser();
            user.setUsername("admin");
            user.setPassword("123456");
            user.setNickname("超级管理员");
            user.setEmail("1148926496@qq.com");
            systemUserService.initSystemUser(user);
            ApiResponse<Void> response = new ApiResponse<>("超级管理员初始化成功");
            return ResponseEntity.ok(response);
        }catch(Exception e){
            return ResponseEntity.badRequest().body("初始化超级管理员失败: " + e.getMessage());
        }   
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody SystemUser loginUser){
            try{
                Map<String,Object> user = systemUserService.authentication(loginUser.getUsername(), loginUser.getPassword());
                if(user == null){
                    ApiResponse<Void> response = ApiResponse.error("用户名或密码错误", 400);
                    return ResponseEntity.badRequest().body(response);
                }
                String token = jwtTokenUtil.generateToken(String.valueOf(user.get("userId")), (String)user.get("username"));
                LoginResponse  loginResponse = new LoginResponse("登录成功", token);
                return ResponseEntity.ok(loginResponse);
            }catch(Exception e){
            return ResponseEntity.badRequest().body(e.getMessage());
    }
}


@GetMapping("/getUserInfo")
public ResponseEntity<?> getUserInfo(HttpServletRequest request){
    try{
        String userId = (String) request.getAttribute("userId");
        Map<String,Object> user = systemUserService.getUserInfoById(Long.valueOf(userId));
        if(user == null){
            ApiResponse<Void> response = ApiResponse.error("用户不存在", 400);
            return ResponseEntity.badRequest().body(response);
        }
        ApiResponse<Map<String,Object>> response = new ApiResponse<>("获取用户信息成功", user);
        return ResponseEntity.ok(response);
    }catch(Exception e){
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

@GetMapping("/getUserMenu")
public ResponseEntity<?> getUserMenu(HttpServletRequest request){
    try{
        String userId = (String) request.getAttribute("userId");
        List<SystemMenu> menuList = systemUserService.getUserMenuByUserId(Long.valueOf(userId));
        ApiResponse<List<SystemMenu>> response = new ApiResponse<>("获取用户菜单成功", menuList);
        return ResponseEntity.ok(response); 
    }catch(Exception e){
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}  
} 

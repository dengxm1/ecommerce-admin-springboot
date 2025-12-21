package com.dengyuheng.ecommerce.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dengyuheng.ecommerce.config.PasswordUtil;
import com.dengyuheng.ecommerce.entity.SystemUser;
import com.dengyuheng.ecommerce.entity.SystemUserRole;
import com.dengyuheng.ecommerce.mapper.SystemUserMapper;
import com.dengyuheng.ecommerce.mapper.SystemUserRoleMapper;
import com.dengyuheng.ecommerce.mapper.SystemRoleMapper;

@Service
public class SystemUserService {
    
    @Autowired
    private SystemUserMapper systemUserService;

    @Autowired
    private PasswordUtil passwordUtil;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired 
    private SystemUserRoleMapper systemUserRoleMapper;

    @Transactional 
   public Long initSystemUser(SystemUser user){
        user.setPassword(passwordUtil.encode(user.getPassword()));
        int result = systemUserService.insertSystemUser(user);
         if (result <= 0) {
            throw new RuntimeException("用户创建失败");
        }
          // 3. 查询超级管理员角色ID（假设角色编码为 SUPER_ADMIN）
        Long roleId = getSuperAdminRoleId();
        
        // 4. 创建用户-角色关联
        SystemUserRole userRole = new SystemUserRole();
        userRole.setUserId(user.getId());  // 获取插入后生成的ID
        userRole.setRoleId(roleId);
        
        systemUserRoleMapper.insertUserRole(userRole);
        
        return user.getId();
   }


    public Map<String, Object> authentication(String username, String password) {
        SystemUser user = systemUserService.findByUsername(username);
        if(user !=null && passwordUtil.matches(password, user.getPassword())){
            Map<String,Object> map =  new HashMap<>();
            map.put("userId",user.getId());
            map.put("username",user.getUsername());
            return map;
        }
        return null;
    }   

    private Long getSuperAdminRoleId() {
        return systemRoleMapper.findIdByCode("TENANT_SUPER_ADMIN");
    }
}

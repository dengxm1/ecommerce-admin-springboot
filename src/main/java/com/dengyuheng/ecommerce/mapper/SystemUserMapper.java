package com.dengyuheng.ecommerce.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.dengyuheng.ecommerce.entity.SystemMenu;
import com.dengyuheng.ecommerce.entity.SystemUser;

@Mapper
public interface SystemUserMapper {
    
    SystemUser findByUsername(String username);

    int insertSystemUser(SystemUser user);
    
    Map<String, Object> getUserInfoById(Long userId);
    
    List<SystemMenu> getUserMenuByUserId(Long userId);
    
}

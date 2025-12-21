package com.dengyuheng.ecommerce.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.dengyuheng.ecommerce.entity.SystemUser;

@Mapper
public interface SystemUserMapper {
    
    SystemUser findByUsername(String username);

    int insertSystemUser(SystemUser user);
}

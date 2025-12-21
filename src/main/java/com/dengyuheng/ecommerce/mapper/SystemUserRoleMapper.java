package com.dengyuheng.ecommerce.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.dengyuheng.ecommerce.entity.SystemUserRole;

@Mapper
public interface SystemUserRoleMapper {

    void insertUserRole(SystemUserRole userRole);
} 


package com.dengyuheng.ecommerce.mapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemRoleMapper {
    Long findIdByCode(String code);
}
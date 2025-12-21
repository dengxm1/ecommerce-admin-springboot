package com.dengyuheng.ecommerce.annotation;

import java.lang.annotation.*;

/**
 * 跳过Token验证的注解
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PassToken {
    boolean value() default true;
}
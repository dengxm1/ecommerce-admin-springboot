package com.dengyuheng.ecommerce.entity;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SystemMenu {
    private Long id;
    private Long parentId;
    private String name;
    private Integer type;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String permission;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

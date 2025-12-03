
# 一、 方案选择
## 是 实现一个 SaaS平台 还是 单商户系统？
### 模式一：多商户SaaS平台 

**场景**：一个像和赞、微盟类似的平台，无数个商家可以在这个平台上注册，每个商家管理自己的业务。

**用户、角色、门店关系：**

- **平台超级管理员**：这个系统的拥有者(我本人)，拥有上帝视角，可以管理平台上所有商户。
- **商户/租户**：每个注册的商家就是一个独立的租户，数据完全隔离。
- **门店**：属于某个商户，一个商户可以有多个门店（连锁店）。


**1. 超级管理员是什么时候分配的？**

- **平台超级管理员：只有一个**，就是在系统初始化时，通过数据库脚本或第一个初始化命令创建的。这个角色不对普通注册开放。
- **商户管理员**：第一个注册该商户账号的人，自动成为该商户的**超级管理员**。

**2. 商户注册流程是怎样的？**

1. 用户访问平台，点击“商家入驻”或“注册”。
2. 填写基本信息：**商户名称**、联系人、手机号、密码等。
3. 注册成功后，系统自动：
    
    - 创建一个新的商户记录。
    - 创建一个**门店记录**（默认门店，门店名可以是商户名）。
    - 将注册用户设置为该商户的**超级管理员**角色。

**3. 权限如何流转？**

- **商户超级管理员**登录后，可以：

    - 创建和管理**本商户**下的其他用户。
    - 创建**本商户**的自定义角色（如“店员”、“店长”）。
    - 为这些角色分配**本商户**范围内的权限。
    - 管理**本商户**下的多个门店。

### 模式二：单商户独立系统
**场景**：为**某一个**商家定制开发的一套独立系统。系统部署在客户自己的服务器上。

**用户、角色关系**：

- **系统超级管理员**：只有一个，就是系统初始化时创建的。
- **普通管理员**：由超级管理员创建，分配不同权限。

**1. 超级管理员是什么时候分配的？**

- 在项目部署后，通过一个**初始化脚本**或访问特定URL（如 /install）来创建第一个超级管理员账号。
- 普通用户无法通过注册成为超级管理员。

**2. 注册功能是否存在？**

- 通常没有开放注册功能。
- 所有用户都由超级管理员在系统内部创建和分配。

**3. 门店如何设计？**

- 在 `system_menu` 中有一个“门店管理”的菜单。
- 超级管理员或具有权限的用户，可以在系统中创建和管理多个门店。
- 用户在创建时，可以指定其所属的门店。

### 最终决策

基于业务复杂度和学习成本的考虑，我决定先从模式二入手，从易到难，先打造一个单商户独立系统，同时在数据库系统设计时，**预留多租户扩展能力**，方便后续**升级成复杂度更高的多商户SaaS平台**。


# 二、模块分类与设计

## 预留扩展租户表

> 新增租户表（`system_tenant` 核心、用于预留多租户扩展功能）

这是所有扩展的基础。

```
CREATE TABLE `system_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `name` varchar(255) NOT NULL COMMENT '租户/商户名称',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:启用, 0:禁用)',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 添加唯一约束
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='租户/商户表';

INSERT INTO system_tenant (id, name) VALUES  (1, '默认商户');

```

## 1. 系统管理

**用户使用流程说明**

- **1. 超级管理员：**

  - 进入「菜单管理」配置系统所有可用的菜单和按钮权限
  - 进入「角色管理」创建角色，并为角色分配菜单/按钮权限
  - 进入「用户管理」创建用户，为用户分配角色

- **2. 普通用户：**

  - 登录系统时，根据所属角色获取对应的菜单权限
  - 在页面上，根据按钮权限显示或隐藏操作按钮


### 1.1 菜单管理 (`system/menu`)

- **功能：动态构建系统导航骨架**。 创建和修改侧边栏菜单项。

- **字段设计**：

```
  id: 唯一ID
  
  parent_id: 父级ID，用于构建多级菜单（0代表一级菜单）
  
  name: 菜单名称（如：“订单中心”）
  
  type: 类型（目录、菜单、按钮）
  
  icon: 菜单图标
  
  path: 前端路由路径（如：/orders）
  
  component: 对应的Vue组件文件路径（如：@/views/order/index.vue）
  
  sort: 排序号
```

### 1.2 用户管理 (`system/user`)

 - **功能**：将**用户**与**角色**关联起来。
 - **操作**:

    1. 在用户列表，点击“分配角色”。
    2. 从角色列表（如“超级管理员”、“商品运营”、“订单客服”）中，为该用户选择一个或多个角色。
    3. 系统后端会存储为：**用户ID** 关联 一个或多个 **角色ID**。

### 1.3 角色管理 (`system/role`)

- **功能**：这是**授权的核心**。在这里为每个角色（如“管理员”、“客服”）分配具体的权限。
- **操作**

    1. 创建角色（如“运营专员”）。
    2. 点击“分配权限”，弹出对话框，展示从【权限管理】那里生成的**权限树**。
    3. 勾选这个角色所能访问的所有菜单和操作按钮。
    4. 系统后端存储为：**角色ID** 关联 一堆 **权限码**。

### 1.4 前端路由配置对应关系

```
// 前端路由结构 - 这对应了菜单的层级
// router/index.js
const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      // 首页
      {
        path: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'ep:home' }
      },
      
      {
        path: 'system',
        meta: { 
          title: '系统管理', 
          icon: 'ep:setting'
        },
        children: [
          {
            path: 'user',
            name: 'UserManagement',  // 给路由命名
            component: () => import('@/views/system/user/index.vue'),
            meta: { title: '用户管理', icon: 'ep:user' }
          },
          {
            path: 'role',
            name: 'RoleManagement',
            component: () => import('@/views/system/role/index.vue'),
            meta: { title: '角色管理', icon: 'ep:lock' }
          },
          {
            path: 'menu',
            name: 'MenuManagement', 
            component: () => import('@/views/system/menu/index.vue'),
            meta: { title: '菜单管理', icon: 'ep:menu' }
          }
        ]
      }
];
```

### 1.5 数据库表详细设计

#### 1.5.1 
#### 1.5.1 菜单表 (`system_menu` 区分平台级和租户级菜单)

**作用**：存储整个系统的导航结构，包括目录、菜单和按钮。

> 说明：为了预留SaaS功能，需要区分平台级和租户级菜单，平台级菜单用于系统的管理员对各组租户商家的管理。

> 新增租户ID字段`tenant_id` (0:平台级菜单, >0:租户级菜单) ，目前是单商户系统，所以组户级值设置为1

| 字段名	|  类型	| 键	|为空	| 说明 |
|--|--|--|--|--|
|  `id`	| `bigint` |	 PK	| NOT	| 主键，自增 |
| `tenant_id` |	`bigint`	 | FK |	NOT, Default: 0	| 租户ID(0:平台级菜单, >0:租户级菜单) |
| `parent_id` |	`bigint`	 | FK |	NOT, Default: 0	| 父级菜单ID，0表示一级菜单 |
| `name`	|`varchar(64)`	|	| NOT	| 菜单名称（如“订单中心”）|
| `type`	| `tinyint` |		| NOT | 类型：1-目录 2-菜单 3-按钮 |
| `path` |	`varchar(255)` |		| YES |	前端路由路径（如 /orders），类型为按钮时可空 |
| `component`	| `varchar(255)` |		| YES	|Vue组件路径（如 @/views/order/index.vue），目录和按钮时可空|
| `icon`	| `varchar(64)` |		|YES	| 图标名称（如 “ep:goods”）|
| `sort` |	`int` |		| NOT, Default: 0	| 同级菜单中的排序，数字越小越靠前 |
| `permission` |	`varchar(100)` |		| YES	| 权限标识符（如 “order:delete”），类型为按钮时必填 |
| `is_visible`	| `tinyint` |		| NOT, Default: 1	| 侧边栏是否显示（1显示，0隐藏），用于一些默认存在的页面（如首页）|
| `created_at` |	`datetime` |		| NOT |	创建时间 |
| `updated_at` |	`datetime`	|	| YES	| 更新时间 |

**建表SQL语句：**

```
CREATE TABLE `system_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID(0:平台级菜单, >0:租户级菜单)',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父级菜单ID',
  `name` varchar(64) NOT NULL COMMENT '菜单名称',
  `type` tinyint NOT NULL COMMENT '1-目录 2-菜单 3-按钮',
  `path` varchar(255) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(64) DEFAULT NULL COMMENT '图标',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `is_visible` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示(1:是, 0:否)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_permission` (`tenant_id`, `permission`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  CONSTRAINT `fk_menu_tenant` FOREIGN KEY (tenant_id) REFERENCES system_tenant(id)  ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='系统菜单表';

-- 初始化租户级菜单（tenant_id = 1）
SET @system_id = 0;
SET @product_id = 0;
SET @order_id = 0;
SET @warehouse_id = 0;
SET @marketing_id = 0;
SET @member_id = 0;
SET @finance_id = 0;
SET @analysis_id = 0; 
SET @settings_id = 0; 

-- 插入父菜单并获取ID
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '系统管理', 1, '/system', NULL, 'ep:setting', 1, 'system:view', 1);
SET @system_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '商品管理', 1, '/product', NULL, 'ep:goods', 2, 'product:view', 1);
SET @product_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '订单管理', 1, '/order', NULL, 'ep:sold-out', 3, 'order:view', 1);
SET @order_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '仓库管理', 1, '/warehouse', NULL, 'ep:office-buiding', 4, 'warehouse:view', 1);
SET @warehouse_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '营销管理', 1, '/marketing', NULL, 'ep:promotion', 5, 'marketing:view', 1);
SET @marketing_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '会员管理', 1, '/member', NULL, 'ep:user-filled', 6, 'member:view', 1);
SET @member_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '财务管理', 1, '/finance', NULL, 'ep:money', 7, 'finance:view', 1);
SET @finance_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '数据分析', 1, '/analysis', NULL, 'ep:data-analysis', 8, 'analysis:view', 1);
SET @analysis_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(1, 0, '系统设置', 1, '/settings', NULL, 'ep:tools', 9, 'settings:view', 1);
SET @settings_id = LAST_INSERT_ID();

-- 插入子菜单
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES
(1, @system_id, '用户管理', 2, '/system/user', 'system/user/index', 'ep:user', 1, 'system:user:view', 1),
(1, @system_id, '角色管理', 2, '/system/role', 'system/role/index', 'ep:lock', 2, 'system:role:view', 1),
(1, @system_id, '菜单管理', 2, '/system/menu', 'system/menu/index', 'ep:menu', 3, 'system:menu:view', 1),
(1, @product_id, '商品列表', 2, '/product/list', 'product/list/index', 'ep:list', 1, 'product:list:view', 1),
(1, @product_id, '分类管理', 2, '/product/category', 'product/category/index', 'ep:grid', 2, 'product:category:view', 1),
(1, @order_id, '订单列表', 2, '/order/list', 'order/list/index', 'ep:document', 1, 'order:list:view', 1),
(1, @warehouse_id, '仓库列表', 2, '/warehouse/list', 'warehouse/list/index', 'ep:list', 1, 'warehouse:list:view', 1),
(1, @warehouse_id, '库存管理', 2, '/warehouse/inventory', 'warehouse/inventory/index', 'ep:box', 2, 'warehouse:inventory:view', 1),
(1, @warehouse_id, '库位管理', 2, '/warehouse/location', 'warehouse/location/index', 'ep:location', 3, 'warehouse:location:view', 1),
(1, @marketing_id, '优惠券', 2, '/marketing/coupon', 'marketing/coupon/index', 'ep:discount', 1, 'marketing:coupon:view', 1),
(1, @marketing_id, '促销活动', 2, '/marketing/promotion', 'marketing/promotion/index', 'ep:present', 2, 'marketing:promotion:view', 1),
(1, @member_id, '会员列表', 2, '/member/list', 'member/list/index', 'ep:list', 1, 'member:list:view', 1),
(1, @member_id, '会员等级', 2, '/member/level', 'member/level/index', 'ep:sort-up', 2, 'member:level:view', 1),
(1, @finance_id, '营销统计', 2, '/finance/revenue', 'finance/revenue/index', 'ep:trend-charts', 1, 'finance:revenue:view', 1),
(1, @finance_id, '提现管理', 2, '/finance/withdraw', 'finance/withdraw/index', 'ep:bank-card', 2, 'finance:withdraw:view', 1),
(1, @analysis_id, '销售分析', 2, '/analysis/sales', 'analysis/sales/index', 'ep:pie-chart', 1, 'analysis:sales:view', 1),
(1, @analysis_id, '客户分析', 2, '/analysis/customer', 'analysis/customer/index', 'ep:user', 2, 'analysis:customer:view', 1),
(1, @settings_id, '基础设置', 2, '/settings/basic', 'settings/basic/index', 'ep:setting', 1, 'settings:basic:view', 1),
(1, @settings_id, '支付设置', 2, '/settings/payment', 'settings/payment/index', 'ep:credit-card', 2, 'settings:payment:view', 1);


-- 初始化平台级菜单--saas预留扩展功能，单商户系统不需要（tenant_id = 0）
INSERT INTO `system_menu` (`tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES
(0, 0, '系统管理', 1, '/system', NULL, 'ep:setting', 100, 'system:manage', 1),
(0, 1, '租户管理', 2, '/system/tenant', 'system/tenant/index', 'ep:office-building', 101, 'system:tenant:view', 1),
(0, 1, '用户管理', 2, '/system/user', 'system/user/index', 'ep:user', 102, 'system:user:view', 1),
(0, 1, '角色管理', 2, '/system/role', 'system/role/index', 'ep:lock', 103, 'system:role:view', 1),
(0, 1, '菜单管理', 2, '/system/menu', 'system/menu/index', 'ep:menu', 104, 'system:menu:view', 1);
```
#### 1.5.2 角色表 (`system_role` 租户内角色编码唯一)

> 租户内角色编码唯一:  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)

**作用**：存储角色定义。

| 字段名 |	类型	| 键	| 为空 |	说明 |
|--|--|--|--|--|
| `id` |	`bigint`	| PK	| NOT |	主键，自增 |
| `tenant_id`| `bigint` | FK |NOT NULL DEFAULT 1| COMMENT '租户ID'|
| `name`	| `varchar(64)` |		| NOT |	角色名称（如“超级管理员”）|
| `code`	| `varchar(64)` |	UNI |	NOT	| 角色编码（如` “SUPER_ADMIN”`），用于编程逻辑判断 |
| `description` |	`varchar(255)`	|	| YES |	角色描述 |
| `created_at`	| `datetime `|		| NOT |	创建时间 |
| `updated_at`	| `datetime` |		| YES |	更新时间 |

**建表SQL语句：**

```
CREATE TABLE `system_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) NOT NULL COMMENT '角色名称',
  `code` varchar(64) NOT NULL COMMENT '角色编码',
  `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
  KEY `idx_tenant_id` (`tenant_id`),
  CONSTRAINT `fk_role_tenant` FOREIGN KEY (tenant_id) REFERENCES system_tenant(id)  
) ENGINE=InnoDB COMMENT='系统角色表';

-- 初始化超级管理员角色数据
INSERT INTO `system_role` (`tenant_id`, `name`, `code`, `description`) VALUES
(1, '租户超级管理员', 'TENANT_SUPER_ADMIN', '租户最高权限管理员');

```
#### 1.5.3 用户表 (`system_user` 租户内用户名唯一)

**作用**：存储系统用户信息。这里只列与权限相关的核心字段，你可以根据业务扩展。

| 字段名 |	类型	| 键 |	为空 |	说明 |
|--|--|--|--|--|
| `id	`| `bigint`	| PK |	NOT|	| 主键，自增 |
| `tenant_id`| `bigint` | FK |NOT NULL DEFAULT 1| COMMENT '租户ID'|
| `username`	| `varchar(64)`	| UNI	| NOT |	用户名，用于登录 |
| `nickname` |`varchar(100)` |DEFAULT NULL| COMMENT '用户昵称'|
| `password` |	`varchar(255)`	|	| NOT	| 加密后的密码 |
| `email`	| `varchar(128)`|		| YES	| 邮箱 |
| `phone`|  `varchar(20)` |DEFAULT NULL |COMMENT '手机号'|
|`avatar`| `varchar(255)` |DEFAULT NULL| COMMENT '头像URL'|
| `is_enabled` |	`tinyint` |		| NOT, Default: 1	| 账户是否启用（1启用，0禁用）|
| `last_login_time`| `datetime` | DEFAULT NULL| COMMENT '最后登录时间'|
| `created_at` |	`datetime` |		| NOT	| 创建时间 |
| `updated_at` |	`datetime` |		| YES	| 更新时间|

**建表SQL语句：**

```
CREATE TABLE `system_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `nickname` varchar(100) DEFAULT NULL COMMENT '用户昵称',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `is_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1:是, 0:否)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
  UNIQUE KEY `uk_tenant_phone` (`tenant_id`, `phone`),
  KEY `idx_tenant_id` (`tenant_id`),
  CONSTRAINT `fk_user_tenant` FOREIGN KEY (tenant_id) REFERENCES system_tenant(id) ON DELETE CASCADE ON UPDATE CASCADE 
) ENGINE=InnoDB COMMENT='系统用户表';

-- 初始化租户管理员用户数据（密码需要加密，这里用明文示意）
INSERT INTO `system_user` (`tenant_id`, `username`, `password`, `nickname`, `email`) VALUES
(1, 'admin', '123456', '租户管理员', '1148926496@qq.com');
```
#### 1.5.4 角色菜单关联表(`system_role_menu`)

**作用**：角色和菜单/权限的多对多关联表。**这是授权的核心**。

| 字段名	| 类型 |	键 |	为空 |	说明 |
|--|--|--|--|--|
| `role_id` |	`bigint`	 |PK FK |	NOT |	角色ID |
| `menu_id` |	`bigint`	|PK FK|	NOT|	菜单ID|
| `created_at` |	`datetime` |		| NOT	| 创建时间 |

**建表SQL语句：**

```
CREATE TABLE `system_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_menu_id` (`menu_id`),
  CONSTRAINT `fk_role_menu_role` FOREIGN KEY (role_id) references system_role (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_role_menu_menu` FOREIGN KEY (menu_id) REFERENCES system_menu (id) ON DELETE CASCADE ON UPDATE cascade
) ENGINE=InnoDB COMMENT='角色菜单关联表';

-- 为租户超级管理员分配所有租户级菜单权限
INSERT INTO `system_role_menu` (`role_id`, `menu_id`) 
SELECT 1, id FROM `system_menu` WHERE tenant_id = 1;

```

#### 1.5.5 用户角色关联表(`system_user_role`)

**作用**：用户和角色的多对多关联表。

| 字段名 |	类型 |	键 |	为空 |	说明 |
|--|--|--|--|--|
| `user_id`	| `bigint`	|PK FK	| NOT	| 用户ID  |
| `role_id`	| `bigint` |PK	FK |	NOT	| 角色ID |
| `created_at` |	`datetime` |		| NOT	| 创建时间 |

**建表SQL语句：**

```
CREATE TABLE `system_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (user_id) REFERENCES system_user (id),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (role_id) REFERENCES system_role (id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

-- 初始化用户角色关联表
INSERT INTO `system_user_role` (`user_id`, `role_id`) VALUES
(1, 1); -- 租户超级管理员
```

#### 关键设计说明

1. **租户隔离**：所有表都有 tenant_id 字段，为SaaS化做准备
2. **菜单分级**：`tenant_id=0` 是平台级菜单，`tenant_id>0` 是租户级菜单
3. **唯一约束**: 使用 `(tenant_id, field)` 组合唯一键，保证租户内数据唯一性
4. **初始化数据**:包含默认的租户、菜单、角色和用户，开箱即用


# 三、其他说明

## 完整的路由表

```
const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      // ==================== 首页 ====================
      {
        path: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { 
          title: '首页', 
          icon: 'ep:home'
        }
      },
      
      // ==================== 系统管理 ====================
      {
        path: 'system',
        meta: { 
          title: '系统管理', 
          icon: 'ep:setting'
          permission: 'system::view'
        },
        children: [
          {
            path: 'user',
            component: () => import('@/views/system/user/index.vue'),
            meta: { 
              title: '用户管理', 
              icon: 'ep:user',
              permission: 'system:user:view'
            }
          },
          {
            path: 'role',
            component: () => import('@/views/system/role/index.vue'),
            meta: { 
              title: '角色管理', 
              icon: 'ep:lock',
              permission: 'system:role:view'
            }
          },
          {
            path: 'menu',
            component: () => import('@/views/system/menu/index.vue'),
            meta: { 
              title: '菜单管理', 
              icon: 'ep:menu',
              permission: 'system:menu:view'
            }
          }
        ]
      },
      
      // ==================== 商品管理 ====================
      {
        path: 'product',
        meta: { 
          title: '商品管理', 
          icon: 'ep:goods'
          permission: 'product:view'
        },
        children: [
          {
            path: 'list',
            component: () => import('@/views/product/list/index.vue'),
            meta: { 
              title: '商品列表', 
              icon: 'ep:list',
              permission: 'product:list:view'
            }
          },
          {
            path: 'category',
            component: () => import('@/views/product/category/index.vue'),
            meta: { 
              title: '分类管理', 
              icon: 'ep:grid',
              permission: 'product:category:view'
            }
          }
        ]
      },
      
      // ==================== 订单管理 ====================
      {
        path: 'order',
        meta: { 
          title: '订单管理', 
          icon: 'ep:sold-out'
          permission: 'order:view'
        },
        children: [
          {
            path: 'list',
            component: () => import('@/views/order/list/index.vue'),
            meta: { 
              title: '订单列表', 
              icon: 'ep:document',
              permission: 'order:list:view'
            }
          }
        ]
      },
      
   // ==================== 仓库与库存管理 ====================
  {
    path: 'warehouse',
    meta: { 
      title: '仓库管理', 
      icon: 'ep:office-building',
      permission: 'warehouse:view'
    },
    children: [
      {
        path: 'list',
        component: () => import('@/views/warehouse/list/index.vue'),
        meta: { 
          title: '仓库列表', 
          icon: 'ep:list',
          permission: 'warehouse:list:view'
        }
      },
      {
        path: 'inventory',
        component: () => import('@/views/warehouse/inventory/index.vue'),
        meta: { 
          title: '库存管理', 
          icon: 'ep:box',
          permission: 'warehouse:inventory:view'
        }
      },
      {
        path: 'location',
        component: () => import('@/views/warehouse/location/index.vue'),
        meta: { 
          title: '库位管理', 
          icon: 'ep:location',
          permission: 'warehouse:location:view'
        }
      }
    ]
  },
      
      // ==================== 营销管理 ====================
      {
        path: 'marketing',
        meta: { 
          title: '营销管理', 
          icon: 'ep:promotion',
           permission: 'marketing:view'
        },
        children: [
          {
            path: 'coupon',
            component: () => import('@/views/marketing/coupon/index.vue'),
            meta: { 
              title: '优惠券', 
              icon: 'ep:discount',
              permission: 'marketing:coupon:view'
            }
          },
          {
            path: 'promotion',
            component: () => import('@/views/marketing/promotion/index.vue'),
            meta: { 
              title: '促销活动', 
              icon: 'ep:present',
              permission: 'marketing:promotion:view'
            }
          }
        ]
      },
      
      // ==================== 会员管理 ====================
      {
        path: 'member',
        meta: { 
          title: '会员管理', 
          icon: 'ep:user-filled',
           permission: 'member:view'
        },
        children: [
          {
            path: 'list',
            component: () => import('@/views/member/list/index.vue'),
            meta: { 
              title: '会员列表', 
              icon: 'ep:list',
              permission: 'member:list:view'
            }
          },
          {
            path: 'level',
            component: () => import('@/views/member/level/index.vue'),
            meta: { 
              title: '会员等级', 
              icon: 'ep:sort-up',
              permission: 'member:level:view'
            }
          }
        ]
      },
      
      // ==================== 财务管理 ====================
      {
        path: 'finance',
        meta: { 
          title: '财务管理', 
          icon: 'ep:money',
          permission: 'finance:view'
        },
        children: [
          {
            path: 'revenue',
            component: () => import('@/views/finance/revenue/index.vue'),
            meta: { 
              title: '营收统计', 
              icon: 'ep:trend-charts',
              permission: 'finance:revenue:view'
            }
          },
          {
            path: 'withdraw',
            component: () => import('@/views/finance/withdraw/index.vue'),
            meta: { 
              title: '提现管理', 
              icon: 'ep:bank-card',
              permission: 'finance:withdraw:view'
            }
          }
        ]
      },
      
      // ==================== 数据分析 ====================
      {
        path: 'analysis',
        meta: { 
          title: '数据分析', 
          icon: 'ep:data-analysis',
          permission: 'analysis:view'
        },
        children: [
          {
            path: 'sales',
            component: () => import('@/views/analysis/sales/index.vue'),
            meta: { 
              title: '销售分析', 
              icon: 'ep:pie-chart',
              permission: 'analysis:sales:view'
            }
          },
          {
            path: 'customer',
            component: () => import('@/views/analysis/customer/index.vue'),
            meta: { 
              title: '客户分析', 
              icon: 'ep:user',
              permission: 'analysis:customer:view'
            }
          }
        ]
      },
      
      // ==================== 系统设置 ====================
      {
        path: 'settings',
        meta: { 
          title: '系统设置', 
          icon: 'ep:tools',
          permission: 'settings:view'
        },
        children: [
          {
            path: 'basic',
            component: () => import('@/views/settings/basic/index.vue'),
            meta: { 
              title: '基础设置', 
              icon: 'ep:setting',
              permission: 'settings:basic:view'
            }
          },
          {
            path: 'payment',
            component: () => import('@/views/settings/payment/index.vue'),
            meta: { 
              title: '支付设置', 
              icon: 'ep:credit-card',
              permission: 'settings:payment:view'
            }
          }
        ]
      }
    ]
  },
  
  // ==================== 登录页 ====================
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    meta: { 
      title: '登录',
      hidden: true  // 对应 is_visible = 0
    }
  },
  
  // ==================== 404页面 ====================
  {
    path: '/404',
    component: () => import('@/views/error/404.vue'),
    meta: { 
      title: '404',
      hidden: true  // 对应 is_visible = 0
    }
  },
  
  // ==================== 重定向页 ====================
  {
    path: '/redirect',
    component: Layout,
    meta: { hidden: true },  // 对应 is_visible = 0
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  
  // ==================== 个人中心 ====================
  {
    path: 'profile',
    component: () => import('@/views/profile/index.vue'),
    meta: { 
      title: '个人中心',
      hidden: true  // 对应 is_visible = 0
    }
  }
];

```
## 初始化超级管理员的正确流程

> 单商户系统的租户级管理员不开放注册入口，一般初始化操作用脚本进行

创建一个完整的初始化SQL脚本：

```
-- 1. 创建租户（单商户场景，tenant_id = 1）
INSERT INTO `system_tenant` (`id`, `name`) VALUES 
(1, '默认商户');

-- 2. 创建超级管理员角色（tenant_id = 1）
INSERT INTO `system_role` (`tenant_id`, `name`, `code`, `description`) VALUES 
(1, '租户超级管理员', 'TENANT_SUPER_ADMIN', '租户最高权限管理员');

-- 3. 创建超级管理员用户（密码需要加密）
-- 假设密码是"admin123"，使用BCrypt加密后
INSERT INTO `system_user` (`tenant_id`, `username`, `password`, `nickname`, `email`) VALUES 
(1, 'admin', '$2a$10$YourEncryptedPasswordHere', '超级管理员', 'admin@example.com');

-- 4. 关联用户和角色
INSERT INTO `system_user_role` (`user_id`, `role_id`) 
SELECT u.id, r.id 
FROM `system_user` u, `system_role` r 
WHERE u.username = 'admin' AND r.code = 'TENANT_SUPER_ADMIN';

-- 5. 为超级管理员角色分配所有菜单权限（只分配租户级菜单）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`) 
SELECT r.id, m.id 
FROM `system_role` r, `system_menu` m 
WHERE r.code = 'TENANT_SUPER_ADMIN' AND m.tenant_id = 1;
```



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

> 租户表（`system_tenant` 核心、用于预留多租户扩展功能）

这是所有扩展的基础。

**设计目的**：为系统提供租户维度的数据隔离基础。本表的 `id` 字段将作为其他业务表（如用户表、角色表）中的外键 `tenant_id`，实现：

  1. **数据归属**：明确每条数据属于哪个租户
  2. **查询隔离**：通过 `tenant_id` 条件筛选特定租户的数据
  3. **权限控制**：确保用户只能访问所属租户的数据

**关联关系**：

  - `system_user.tenant_id` → `system_tenant.id`
  - `system_role.tenant_id` → `system_tenant.id`
  - 其他业务表的 `tenant_id` 均引用此表

**建表SQL语句：**

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

  - 进入「角色管理」创建角色，并为角色分配菜单/按钮权限
  - 进入「用户管理」创建用户，为用户分配角色

- **2. 普通用户：**

  - 登录系统时，根据所属角色获取对应的菜单权限
  - 在页面上，根据按钮权限显示或隐藏操作按钮


### 1.1 用户管理 (`system/user`)

 - **功能**：管理系统的用户账户，包括创建、编辑、启用/禁用用户，以及为用户分配角色。
 - **核心操作:**:

    1. **创建用户**：添加新用户账户，设置用户名、密码、基本信息等
    2. **编辑用户**：修改用户的基本信息和账户状态
    3. **启用/禁用**：控制用户账户的登录权限
    4. **分配角色**：将用户关联到特定角色，从而继承角色的权限集合
    5. **重置密码**：为用户重置登录密码

- **权限控制：**

   - 只有超级管理员和有用户管理权限的管理员可以管理用户
   - 用户只能被分配到自己权限范围内的角色


### 1.2 角色管理 (`system/role`)

- **功能**：这是**授权的核心**。通过角色管理实现细粒度的权限控制，为不同类型的用户定义不同的访问权限集合。

- **核心操作**

    1. 创建角色：定义新的角色（如"运营专员"、"客服专员"、"财务人员"等）
    2. 编辑角色：修改角色名称、描述等信息
    3. 删除角色：移除不再需要的角色（需确保无用户关联）
    4. 分配权限：为角色配置可访问的菜单和操作按钮权限

- **权限分配流程：**

    1. 在角色列表中选中要配置的角色
    2. 点击"分配权限"按钮，系统展示预定义的权限树
    3. 权限树包含系统的所有菜单项和按钮操作，按模块层级组织
    4. 通过勾选/取消勾选权限节点，为角色配置访问权限
    5. 保存后，该角色下的所有用户将继承这些权限

- **权限树特点：**

  - 采用树形结构展示系统所有预定义菜单和按钮权限
  - 支持按模块展开/收起，方便快速定位
  - 提供全选、反选等批量操作功能
  - 权限继承关系清晰，父节点选中时自动选中所有子节点

- **权限生效机制：**

  1. **权限数据获取** ：用户登录时，后端根据用户关联的角色查询对应的菜单权限集合
  2. **路由动态注册**：前端接收后端返回的菜单/路由配置，动态添加到Vue Router中
  3. **菜单渲染**：侧边栏根据动态注册的路由信息生成导航菜单
  4. **按钮权限控制：**

    - 菜单项通过路由控制访问权限
    - 按钮级操作通过`permission`字段控制，前端根据权限标识显示/隐藏操作按钮
  5. **接口权限验证**：后端接口进行角色和权限验证，防止越权访问

### 1.3 前端路由配置对应关系

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
          }
        ]
      }
];
```

### 1.5 数据库表详细设计

#### 1.5.1 菜单表 (`system_menu` 区分平台级和租户级菜单)

**作用**：存储整个系统的导航结构，包括目录、菜单和按钮。所有菜单在系统初始化时一次性创建，确保结构稳定。

**为什么菜单存在数据库中，而不是直接写在前端代码里？**

说实话，两种方式都能实现权限控制，区别主要在**架构思维**和**开发习惯**上：

  1. **传统RBAC模式**：企业级系统常用做法，把菜单当作"数据"来管理
  2. **后端思维**：权限判断逻辑放在后端更集中，前端只负责展示
  3. **查询方便**：要查"某个角色有哪些权限"，SQL联表比读前端配置文件方便
  4. **统一管理**：菜单、用户、角色都在数据库里，维护起来一致
> 注：其实前端硬编码菜单+后端返回权限标识也能实现同样的权限控制。选择数据库存储更多是遵循传统企业系统架构，同时为未来可能的SaaS升级留个设计基础。


| 字段名	|  类型	| 键	|为空	| 说明 |
|--|--|--|--|--|
|  `id`	| `bigint` |	 PK	| NOT	| 主键，自增 |
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
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级菜单ID',
  `name` varchar(64) NOT NULL COMMENT '菜单名称',
  `type` tinyint NOT NULL COMMENT '1-目录 2-菜单 3-按钮',
  `path` varchar(255) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(64) DEFAULT NULL COMMENT '图标',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `is_visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否显示(1:是, 0:否)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission` (`permission`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB COMMENT='系统菜单表'

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
INSERT INTO `system_menu` ( `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '系统管理', 1, '/system', NULL, 'ep:setting', 1, 'system:view', 1);
SET @system_id = LAST_INSERT_ID();
INSERT INTO `system_menu` ( `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '商品管理', 1, '/product', NULL, 'ep:goods', 2, 'product:view', 1);
SET @product_id = LAST_INSERT_ID();
INSERT INTO `system_menu` ( `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '订单管理', 1, '/order', NULL, 'ep:sold-out', 3, 'order:view', 1);
SET @order_id = LAST_INSERT_ID();
INSERT INTO `system_menu` ( `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '仓库管理', 1, '/warehouse', NULL, 'ep:office-buiding', 4, 'warehouse:view', 1);
SET @warehouse_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '营销管理', 1, '/marketing', NULL, 'ep:promotion', 5, 'marketing:view', 1);
SET @marketing_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '会员管理', 1, '/member', NULL, 'ep:user-filled', 6, 'member:view', 1);
SET @member_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '财务管理', 1, '/finance', NULL, 'ep:money', 7, 'finance:view', 1);
SET @finance_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '数据分析', 1, '/analysis', NULL, 'ep:data-analysis', 8, 'analysis:view', 1);
SET @analysis_id = LAST_INSERT_ID();
INSERT INTO `system_menu` (`parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES 
(0, '系统设置', 1, '/settings', NULL, 'ep:tools', 9, 'settings:view', 1);
SET @settings_id = LAST_INSERT_ID();

-- 插入子菜单
INSERT INTO `system_menu` ( `parent_id`, `name`, `type`, `path`, `component`, `icon`, `sort`, `permission`, `is_visible`) VALUES
(@system_id, '用户管理', 2, '/system/user', 'system/user/index', 'ep:user', 1, 'system:user:view', 1),
(@system_id, '角色管理', 2, '/system/role', 'system/role/index', 'ep:lock', 2, 'system:role:view', 1),
(system_id, '菜单管理', 2, '/system/menu', 'system/menu/index', 'ep:menu', 3, 'system:menu:view', 1),
(@product_id, '商品列表', 2, '/product/list', 'product/list/index', 'ep:list', 1, 'product:list:view', 1),
(@product_id, '分类管理', 2, '/product/category', 'product/category/index', 'ep:grid', 2, 'product:category:view', 1),
(@order_id, '订单列表', 2, '/order/list', 'order/list/index', 'ep:document', 1, 'order:list:view', 1),
(@warehouse_id, '仓库列表', 2, '/warehouse/list', 'warehouse/list/index', 'ep:list', 1, 'warehouse:list:view', 1),
(@warehouse_id, '库存管理', 2, '/warehouse/inventory', 'warehouse/inventory/index', 'ep:box', 2, 'warehouse:inventory:view', 1),
(@warehouse_id, '库位管理', 2, '/warehouse/location', 'warehouse/location/index', 'ep:location', 3, 'warehouse:location:view', 1),
(@marketing_id, '优惠券', 2, '/marketing/coupon', 'marketing/coupon/index', 'ep:discount', 1, 'marketing:coupon:view', 1),
(@marketing_id, '促销活动', 2, '/marketing/promotion', 'marketing/promotion/index', 'ep:present', 2, 'marketing:promotion:view', 1),
(@member_id, '会员列表', 2, '/member/list', 'member/list/index', 'ep:list', 1, 'member:list:view', 1),
(@member_id, '会员等级', 2, '/member/level', 'member/level/index', 'ep:sort-up', 2, 'member:level:view', 1),
(@finance_id, '营销统计', 2, '/finance/revenue', 'finance/revenue/index', 'ep:trend-charts', 1, 'finance:revenue:view', 1),
(@finance_id, '提现管理', 2, '/finance/withdraw', 'finance/withdraw/index', 'ep:bank-card', 2, 'finance:withdraw:view', 1),
(@analysis_id, '销售分析', 2, '/analysis/sales', 'analysis/sales/index', 'ep:pie-chart', 1, 'analysis:sales:view', 1),
(@analysis_id, '客户分析', 2, '/analysis/customer', 'analysis/customer/index', 'ep:user', 2, 'analysis:customer:view', 1),
(@settings_id, '基础设置', 2, '/settings/basic', 'settings/basic/index', 'ep:setting', 1, 'settings:basic:view', 1),
(@settings_id, '支付设置', 2, '/settings/payment', 'settings/payment/index', 'ep:credit-card', 2, 'settings:payment:view', 1);
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
  CONSTRAINT `fk_role_menu_menu` FOREIGN KEY (menu_id) 
  REFERENCES system_menu (id)
  ON DELETE RESTRICT  -- 防止菜单被意外删除
  ON UPDATE CASCADE   -- 菜单ID更新时，关联表同步更新
) ENGINE=InnoDB COMMENT='角色菜单关联表';

-- 为租户超级管理员分配所有租户级菜单权限
INSERT INTO `system_role_menu` (`role_id`, `menu_id`) 
SELECT 1, id FROM `system_menu`;

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
2. **菜单预定义**：系统菜单通过初始化SQL静态定义，确保系统架构的稳定性
3. **权限控制**：通过角色-菜单关联实现细粒度的权限分配
4. **唯一约束**: 使用 `(tenant_id, field)` 组合唯一键，保证租户内数据唯一性
5. **初始化数据**:包含默认的租户、菜单和角色，开箱即用


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
## 单租户系统初始化超级管理员流程

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
WHERE r.code = 'TENANT_SUPER_ADMIN';
```

## 更优雅的菜单表

> 适用于多租户saas系统中，不同租户需要定制化菜单的情况

```
-- 菜单模板表（存标准菜单结构）
CREATE TABLE `menu_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `type` tinyint NOT NULL,
  `path` varchar(255) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `permission` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission` (`permission`)
);

-- 租户菜单表（关联到模板）
CREATE TABLE `tenant_menu` (
  `tenant_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `is_enabled` tinyint DEFAULT 1,
  PRIMARY KEY (`tenant_id`, `template_id`)
);
```
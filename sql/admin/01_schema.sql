-- 部门表（部门链：ancestors 存祖先路径，数据权限按前缀匹配"本部门及以下"）
DROP DATABASE IF EXISTS ntrain_admin;
CREATE DATABASE ntrain_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ntrain_admin;

CREATE TABLE sys_dept (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    parent_id     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '父部门ID，顶级为0',
    ancestors     VARCHAR(255)     NOT NULL DEFAULT '0' COMMENT '部门链，如 0,1,3',
    dept_name     VARCHAR(64)      NOT NULL COMMENT '部门名称',
    order_num     INT              NOT NULL DEFAULT 0 COMMENT '同级显示顺序',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_ancestors (ancestors(64))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '部门表';

-- 管理员表（与用户端 m_user 完全独立，两套账号体系）

CREATE TABLE sys_user (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    username      VARCHAR(32)      NOT NULL COMMENT '登录名，唯一',
    password      VARCHAR(100)     NOT NULL COMMENT 'BCrypt 密文',
    nickname      VARCHAR(64)      NOT NULL COMMENT '显示名',
    phone         VARCHAR(20)               DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(128)              DEFAULT NULL COMMENT '邮箱',
    dept_id       BIGINT UNSIGNED  NOT NULL COMMENT '所属部门',
    user_type     CHAR(1)          NOT NULL DEFAULT 'A' COMMENT '类型：S超级管理员 A普通管理员',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    last_login_at DATETIME                  DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_dept_id (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理员表';

-- 角色表

CREATE TABLE sys_role (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    role_name     VARCHAR(64)      NOT NULL COMMENT '角色名',
    role_key      VARCHAR(64)      NOT NULL COMMENT '角色标识，如 admin / order_manager',
    built_in      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '内置角色：1不可删改',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark        VARCHAR(255)              DEFAULT NULL COMMENT '备注',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色表';

-- 菜单/权限表（目录 M / 菜单 C / 按钮 F 三级；F 节点承载 perms 权限标识）

CREATE TABLE sys_menu (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    parent_id     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '父菜单ID，顶级为0',
    menu_name     VARCHAR(64)      NOT NULL COMMENT '名称',
    menu_type     CHAR(1)          NOT NULL COMMENT '类型：M目录 C菜单 F按钮',
    perms         VARCHAR(128)              DEFAULT NULL COMMENT '权限标识，如 b:order:list',
    path          VARCHAR(128)              DEFAULT NULL COMMENT '前端路由地址',
    component     VARCHAR(128)              DEFAULT NULL COMMENT '前端组件路径',
    icon          VARCHAR(64)               DEFAULT NULL COMMENT '图标',
    order_num     INT              NOT NULL DEFAULT 0 COMMENT '显示顺序',
    visible       TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否显示：1是 0隐藏',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单权限表';

-- 管理员-角色关联

CREATE TABLE sys_user_role (
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '管理员ID',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理员角色关联表';

-- 角色-菜单关联

CREATE TABLE sys_role_menu (
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    menu_id       BIGINT UNSIGNED NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色菜单关联表';

-- 登录日志（用户端/管理端共用，user_type 区分）

CREATE TABLE sys_login_log (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    user_type     CHAR(1)          NOT NULL COMMENT '账号类型：U用户 A管理员',
    user_id       BIGINT UNSIGNED  NOT NULL COMMENT '账号ID',
    username      VARCHAR(64)      NOT NULL COMMENT '登录名/手机号',
    ip            VARCHAR(64)               DEFAULT NULL COMMENT '登录IP',
    user_agent    VARCHAR(255)              DEFAULT NULL COMMENT '浏览器UA',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    msg           VARCHAR(255)              DEFAULT NULL COMMENT '提示消息',
    login_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_user (user_type, user_id),
    KEY idx_login_at (login_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '登录日志表';

-- 操作日志

CREATE TABLE sys_oper_log (
    id            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '管理员ID',
    username      VARCHAR(64)     NOT NULL COMMENT '操作人',
    module        VARCHAR(64)     NOT NULL COMMENT '业务模块',
    operation     VARCHAR(64)     NOT NULL COMMENT '操作动作',
    method        VARCHAR(255)             DEFAULT NULL COMMENT '请求方法',
    params        TEXT                     COMMENT '请求参数（脱敏后）',
    result_code   INT                      DEFAULT NULL COMMENT '响应码',
    ip            VARCHAR(64)              DEFAULT NULL COMMENT '操作IP',
    cost_ms       INT             NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    oper_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_oper_at (oper_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志表';

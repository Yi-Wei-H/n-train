DROP DATABASE IF EXISTS ntrain_member;
CREATE DATABASE ntrain_member CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ntrain_member;

-- 会员账号（与管理员 sys_user 分表；登录用手机号）

CREATE TABLE m_user (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    phone         CHAR(11)         NOT NULL COMMENT '手机号，登录账号，唯一',
    password      VARCHAR(100)     NOT NULL COMMENT 'BCrypt 密文',
    id_card_no    VARCHAR(64)      DEFAULT NULL COMMENT '身份证号（AES 可逆加密存储）',
    nickname      VARCHAR(64)               DEFAULT NULL COMMENT '昵称',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    KEY idx_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员账号表';

-- 乘车人（会员名下，支持儿童/学生票型）

CREATE TABLE m_passenger (
    id             BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    member_id      BIGINT UNSIGNED  NOT NULL COMMENT '所属会员',
    name           VARCHAR(64)      NOT NULL COMMENT '姓名',
    id_type        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '证件类型：1身份证 2港澳台 3护照',
    id_card_no     VARCHAR(64)      NOT NULL COMMENT '证件号（AES 加密存储，读取自动解密，展示层遮掩）',
    phone          VARCHAR(20)               DEFAULT NULL COMMENT '联系电话',
    passenger_type TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类型：1成人 2儿童 3学生',
    created_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    KEY idx_member_idcard (member_id, id_card_no),
    KEY idx_member_id (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '乘车人表';

-- 虚拟钱包（余额上限 1000，DB 层 CHECK 兜底，version 乐观锁）

CREATE TABLE m_wallet (
    id            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    member_id     BIGINT UNSIGNED NOT NULL COMMENT '会员ID，一对一',
    balance       DECIMAL(10, 2)  NOT NULL DEFAULT 0.00 COMMENT '余额（元），上限1000',
    version       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常 0冻结',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_id (member_id),
    CONSTRAINT chk_wallet_balance CHECK (balance >= 0 AND balance <= 1000.00)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '虚拟钱包表';

-- 钱包流水（充值/支付/退款，balance_before/after 可对账）

CREATE TABLE m_wallet_flow (
    id              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    member_id       BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    flow_no         VARCHAR(64)     NOT NULL COMMENT '流水号，唯一',
    flow_type       TINYINT UNSIGNED NOT NULL COMMENT '类型：1充值 2支付 3退款',
    amount          DECIMAL(10, 2)  NOT NULL COMMENT '变动金额（正数）',
    balance_before  DECIMAL(10, 2)  NOT NULL COMMENT '变动前余额',
    balance_after   DECIMAL(10, 2)  NOT NULL COMMENT '变动后余额',
    biz_no          VARCHAR(64)              DEFAULT NULL COMMENT '关联业务号（订单号/充值单号）',
    remark          VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_no (flow_no),
    KEY idx_member_created (member_id, created_at),
    KEY idx_biz_no (biz_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '钱包流水表';
DROP DATABASE IF EXISTS ntrain_business;
CREATE DATABASE ntrain_business CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ntrain_business;

-- 订单主表（主状态机载体）
CREATE TABLE b_order (
    id               BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    order_no         VARCHAR(64)      NOT NULL COMMENT '业务订单号，唯一',
    member_id        BIGINT UNSIGNED  NOT NULL COMMENT '会员ID',
    ride_date        DATE             NOT NULL COMMENT '乘车日期',
    train_code       VARCHAR(20)      NOT NULL COMMENT '车次号',
    from_station     VARCHAR(10)      NOT NULL COMMENT '出发电报码',
    to_station       VARCHAR(10)      NOT NULL COMMENT '到达电报码',
    departure_time   DATETIME         NOT NULL COMMENT '发车时间',
    arrival_time     DATETIME         NOT NULL COMMENT '到达时间',
    total_amount     DECIMAL(10, 2)   NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    order_status     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付(待出行) 2已出行 3已完成 4已取消 5退款中 6已关闭',
    pay_at           DATETIME                  DEFAULT NULL COMMENT '支付时间',
    cancel_at        DATETIME                  DEFAULT NULL COMMENT '取消时间',
    cancel_type      TINYINT UNSIGNED          DEFAULT NULL COMMENT '取消来源：1超时自动 2用户手动',
    closed_at        DATETIME                  DEFAULT NULL COMMENT '关单时间',
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    updated_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_member_status (member_id, order_status),
    KEY idx_date_train (ride_date, train_code),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单主表';

-- 订单明细（每乘车人一票，明细级状态支持部分退票）

CREATE TABLE b_order_item (
    id               BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    order_id         BIGINT UNSIGNED  NOT NULL COMMENT '订单ID',
    member_id        BIGINT UNSIGNED  NOT NULL COMMENT '会员ID',
    passenger_id     BIGINT UNSIGNED  NOT NULL COMMENT '乘车人ID',
    passenger_name   VARCHAR(64)      NOT NULL COMMENT '乘车人姓名（下单快照）',
    id_card_no       VARCHAR(64)      NOT NULL COMMENT '证件号（AES 加密存储，下单快照）',
    ticket_type      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '票型：1成人 2儿童 3学生',
    seat_type        TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    carriage_no      INT                       DEFAULT NULL COMMENT '车厢号（选座后回填）',
    seat_no          VARCHAR(16)               DEFAULT NULL COMMENT '座位号（选座后回填）',
    from_station     VARCHAR(10)      NOT NULL COMMENT '出发电报码',
    to_station       VARCHAR(10)      NOT NULL COMMENT '到达电报码',
    price            DECIMAL(10, 2)   NOT NULL COMMENT '票价',
    item_status      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已出行 3退票中 4已退票',
    refund_amount    DECIMAL(10, 2)            DEFAULT NULL COMMENT '实退金额',
    refund_at        DATETIME                  DEFAULT NULL COMMENT '退票时间',
    refund_batch_no  VARCHAR(64)               DEFAULT NULL COMMENT '退款批次号（apply 写入，confirm 按批次定位）',
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_member (member_id),
    KEY idx_passenger (passenger_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细表';

-- 支付单（钱包扣款记录，与订单一对一/多对一留扩展）

CREATE TABLE b_payment (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    pay_no        VARCHAR(64)      NOT NULL COMMENT '支付单号，唯一（幂等键）',
    order_id      BIGINT UNSIGNED  NOT NULL COMMENT '订单ID',
    member_id     BIGINT UNSIGNED  NOT NULL COMMENT '会员ID',
    amount        DECIMAL(10, 2)   NOT NULL COMMENT '支付金额',
    pay_type      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '方式：1钱包余额（预留2支付宝3微信）',
    pay_status    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0处理中 1成功 2失败',
    pay_at        DATETIME                  DEFAULT NULL COMMENT '支付成功时间',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付单表';

CREATE TABLE b_ticket (
    id             BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    member_id      BIGINT UNSIGNED  NOT NULL COMMENT '会员ID',
    order_id       BIGINT UNSIGNED  NOT NULL COMMENT '订单ID',
    order_item_id  BIGINT UNSIGNED  NOT NULL COMMENT '订单明细ID',
    passenger_id   BIGINT UNSIGNED  NOT NULL COMMENT '乘车人ID',
    passenger_name VARCHAR(64)      NOT NULL COMMENT '乘车人姓名',
    id_card_no     VARCHAR(64)      NOT NULL COMMENT '证件号（AES 加密存储，展示层遮掩）',
    ride_date      DATE             NOT NULL COMMENT '乘车日期',
    train_code     VARCHAR(20)      NOT NULL COMMENT '车次号',
    from_station   VARCHAR(64)      NOT NULL COMMENT '出发站名',
    to_station     VARCHAR(64)      NOT NULL COMMENT '到达站名',
    departure_time DATETIME         NOT NULL COMMENT '发车时间',
    arrival_time   DATETIME         NOT NULL COMMENT '到达时间',
    seat_type      TINYINT UNSIGNED NOT NULL COMMENT '座位类型：1商务座 2一等座 3二等座 4硬卧...',
    carriage_no    INT                       DEFAULT NULL COMMENT '车厢号',
    seat_no        VARCHAR(16)               DEFAULT NULL COMMENT '座位号',
    ticket_status  TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1待出行 2已出行 3已退票 4已失效(订单取消)',
    price          DECIMAL(10, 2)   NOT NULL COMMENT '票价',
    created_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_member (member_id),
    KEY idx_order_item (order_item_id),
    KEY idx_date_train (ride_date, train_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车票表（随订单域存储）';

-- 退款批次（申请 → 会员域入账 → 确认；batchNo 是全链路幂等键）
CREATE TABLE b_refund_batch (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    batch_no      VARCHAR(64)      NOT NULL COMMENT '退款批次号，唯一（钱包流水 biz_no / 确认幂等键）',
    order_id      BIGINT UNSIGNED  NOT NULL COMMENT '订单ID',
    member_id     BIGINT UNSIGNED  NOT NULL COMMENT '会员ID',
    refund_amount DECIMAL(10, 2)   NOT NULL COMMENT '退款总额',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0处理中(等钱包入账后确认) 1已确认',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    confirmed_at  DATETIME                  DEFAULT NULL COMMENT '确认时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '退款批次表';

-- =====================================================================
-- ntrain 业务域（b_）—— 车次/车站基础数据、每日车次余票、订单
-- Flyway 基线 V3，在 ntrain 库执行
-- =====================================================================

-- 车次基础表

CREATE TABLE b_train (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    code          VARCHAR(20)      NOT NULL COMMENT '车次号，如 G101，唯一',
    train_type    TINYINT UNSIGNED NOT NULL COMMENT '类型：1高铁 2动车 3普快 4直达',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车次表';

-- 车站表

CREATE TABLE b_station (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    code          VARCHAR(10)      NOT NULL COMMENT '电报码，如 BJP，唯一',
    name          VARCHAR(64)      NOT NULL COMMENT '站名',
    pinyin        VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '拼音（搜索用）',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车站表';

-- 车次-车站经停表（sort 定序，含到发时刻）

CREATE TABLE b_train_station (
    id              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    train_id        BIGINT UNSIGNED NOT NULL COMMENT '车次ID',
    station_id      BIGINT UNSIGNED NOT NULL COMMENT '车站ID',
    sort            INT             NOT NULL COMMENT '经停顺序，1为始发',
    departure_time  TIME                     DEFAULT NULL COMMENT '发车时刻（终点站为NULL）',
    arrival_time    TIME                     DEFAULT NULL COMMENT '到达时刻（始发站为NULL）',
    distance_km     INT                      DEFAULT NULL COMMENT '距始发站公里数',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_station (train_id, station_id),
    KEY idx_station_id (station_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车次经停表';

-- 车次-车厢模板表

CREATE TABLE b_train_carriage (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    train_id      BIGINT UNSIGNED  NOT NULL COMMENT '车次ID',
    carriage_no   INT              NOT NULL COMMENT '车厢号，从1起',
    seat_type     TINYINT UNSIGNED NOT NULL COMMENT '座位类型：1商务座 2一等座 3二等座 4硬卧 5硬座...',
    seat_count    INT              NOT NULL DEFAULT 0 COMMENT '座位数',
    row_count     INT              NOT NULL DEFAULT 0 COMMENT '排数',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_carriage (train_id, carriage_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车次车厢表';

-- 车次-座位模板表（sell 为站序位图，如 0100：中间站区间被占）

CREATE TABLE b_train_seat (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    train_id      BIGINT UNSIGNED  NOT NULL COMMENT '车次ID',
    carriage_no   INT              NOT NULL COMMENT '车厢号',
    row_no        INT              NOT NULL COMMENT '排号',
    col_no        CHAR(2)          NOT NULL COMMENT '列号，如 A/B/C/D/F',
    seat_type     TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    sell          VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '站序售出位图，0空闲 1售出',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seat (train_id, carriage_no, row_no, col_no),
    KEY idx_train (train_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '车次座位表';

-- 每日车次（基础车次按日期物化，Quartz 每日生成）

CREATE TABLE b_daily_train (
    id                  BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    ride_date           DATE             NOT NULL COMMENT '乘车日期',
    train_code          VARCHAR(20)      NOT NULL COMMENT '车次号',
    train_type          TINYINT UNSIGNED NOT NULL COMMENT '类型：1高铁 2动车 3普快 4直达',
    departure_station   VARCHAR(10)      NOT NULL COMMENT '始发电报码',
    arrival_station     VARCHAR(10)      NOT NULL COMMENT '终到电报码',
    departure_time      TIME             NOT NULL COMMENT '始发时刻',
    arrival_time        TIME             NOT NULL COMMENT '终到时刻',
    status              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0未发车 1运行中 2已到达',
    created_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_train (ride_date, train_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '每日车次表';

-- 每日车厢（由模板物化）

CREATE TABLE b_daily_train_carriage (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    ride_date     DATE             NOT NULL COMMENT '乘车日期',
    train_code    VARCHAR(20)      NOT NULL COMMENT '车次号',
    carriage_no   INT              NOT NULL COMMENT '车厢号',
    seat_type     TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    seat_count    INT              NOT NULL DEFAULT 0 COMMENT '座位数',
    row_count     INT              NOT NULL DEFAULT 0 COMMENT '排数',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_train_carriage (ride_date, train_code, carriage_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '每日车厢表';

-- 每日座位（扣减原子操作的对象，sell 位图同模板表）

CREATE TABLE b_daily_train_seat (
    id            BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    ride_date     DATE             NOT NULL COMMENT '乘车日期',
    train_code    VARCHAR(20)      NOT NULL COMMENT '车次号',
    carriage_no   INT              NOT NULL COMMENT '车厢号',
    row_no        INT              NOT NULL COMMENT '排号',
    col_no        CHAR(2)          NOT NULL COMMENT '列号',
    seat_type     TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    sell          VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '站序售出位图，0空闲 1售出',
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seat (ride_date, train_code, carriage_no, row_no, col_no),
    KEY idx_date_train (ride_date, train_code),
    -- 下单系统分配路径的区间空闲过滤（覆盖索引：谓词/排序列全在索引内免回表，uk_seat 唯一约束不动）
    KEY idx_seat_idle (ride_date, train_code, seat_type, carriage_no, row_no, col_no, sell)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '每日座位表';

-- 每日余票/票价（查票主表，下单校验余量）

CREATE TABLE b_daily_train_ticket (
    id                 BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    ride_date          DATE             NOT NULL COMMENT '乘车日期',
    train_code         VARCHAR(20)      NOT NULL COMMENT '车次号',
    from_station       VARCHAR(10)      NOT NULL COMMENT '出发电报码',
    to_station         VARCHAR(10)      NOT NULL COMMENT '到达电报码',
    departure_time     TIME             NOT NULL COMMENT '出发时刻',
    arrival_time       TIME             NOT NULL COMMENT '到达时刻',
    seat_type          TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    remaining          INT              NOT NULL DEFAULT 0 COMMENT '余票数',
    price              DECIMAL(10, 2)   NOT NULL DEFAULT 0.00 COMMENT '票价',
    created_at         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_query (ride_date, train_code, from_station, to_station, seat_type),
    KEY idx_search (ride_date, from_station, to_station)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '每日余票票价表';

-- 座位占用记录（锁座落库凭据；跨服务释放的幂等锚点）
CREATE TABLE b_seat_occupy (
    id           BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    order_no     VARCHAR(64)      NOT NULL COMMENT '订单号（锁座幂等键）',
    order_item_id BIGINT UNSIGNED NOT NULL COMMENT '订单明细ID（退款按明细释放）',
    ride_date    DATE             NOT NULL COMMENT '乘车日期',
    train_code   VARCHAR(20)      NOT NULL COMMENT '车次号',
    from_station VARCHAR(10)      NOT NULL COMMENT '出发电报码',
    to_station   VARCHAR(10)      NOT NULL COMMENT '到达电报码',
    seat_type    TINYINT UNSIGNED NOT NULL COMMENT '座位类型',
    carriage_no  INT              NOT NULL COMMENT '车厢号',
    row_no       INT              NOT NULL COMMENT '排号',
    col_no       VARCHAR(2)       NOT NULL COMMENT '列号',
    status       TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1占用 2已释放（条件更新保证释放幂等）',
    created_at   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item (order_item_id),
    KEY idx_order_no (order_no),
    KEY idx_date_train (ride_date, train_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '座位占用表';

CREATE TABLE b_sk_token
(
    id          BIGINT UNSIGNED  NOT NULL COMMENT '主键(雪花)',
    ride_date   DATE        NOT NULL COMMENT '乘车日期',
    train_code  VARCHAR(20) NOT NULL COMMENT '车次号',
    total_count INT         NOT NULL COMMENT '累计已发放令牌数（初始+补偿追加，只增）',
    remaining   INT         NOT NULL COMMENT '当前剩余（成功订单事务内条件扣减）',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_train (ride_date, train_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '令牌表';
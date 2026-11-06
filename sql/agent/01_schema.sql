-- =====================================================================
-- ntrain_agent 建表脚本（PostgreSQL 14+，需先启用 pgvector 扩展）
--
-- 说明：知识库向量数据不建表 —— 由 Spring AI PgVectorStore 自建自管
-- =====================================================================

DROP DATABASE IF EXISTS ntrain_agent;
CREATE DATABASE ntrain_agent
    WITH ENCODING = 'UTF8'
         LC_COLLATE = 'C'
         LC_CTYPE = 'C'
         TEMPLATE = template0;

-- ---------------------------------------------------------------------
-- 模型预设表：预置常见厂商的接入模板（baseURL + 上游格式 + 认证方式），管理员据此一键接入
-- （初始数据见 02_init_data.sql；仅作创建 a_model 时的模板，创建后快照，改预设不影响已建配置）
-- ---------------------------------------------------------------------
CREATE TABLE a_model_preset (
    id             BIGINT       NOT NULL,
    vendor         VARCHAR(32)  NOT NULL,
    preset_name    VARCHAR(64)  NOT NULL,
    model_type     SMALLINT     NOT NULL,
    base_url       VARCHAR(255) NOT NULL,
    api_format     SMALLINT     NOT NULL DEFAULT 1,
    auth_type      SMALLINT     NOT NULL DEFAULT 1,
    auth_key_name  VARCHAR(64)  NULL,
    api_model_id   VARCHAR(64)  NOT NULL,
    default_params VARCHAR(512) NULL,
    description    VARCHAR(255) NULL,
    sort_no        INT          NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_preset_vendor_model ON a_model_preset (vendor, api_model_id);

COMMENT ON TABLE a_model_preset IS '模型预设表';
COMMENT ON COLUMN a_model_preset.vendor IS '厂商标识：deepseek等';
COMMENT ON COLUMN a_model_preset.preset_name IS '展示名，如 DeepSeek-Chat';
COMMENT ON COLUMN a_model_preset.model_type IS '模型类型：1文字;2向量';
COMMENT ON COLUMN a_model_preset.base_url IS '上游 API 地址';
COMMENT ON COLUMN a_model_preset.api_format IS '上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic(messages) 4 Gemini(generateContent)';
COMMENT ON COLUMN a_model_preset.auth_type IS '认证方式：1 Bearer头 2 Key头直传 3 查询参数';
COMMENT ON COLUMN a_model_preset.auth_key_name IS '认证字段名';
COMMENT ON COLUMN a_model_preset.api_model_id IS '厂商侧模型 ID';
COMMENT ON COLUMN a_model_preset.default_params IS '默认参数快照：temperature/max_tokens 等';
COMMENT ON COLUMN a_model_preset.sort_no IS '展示排序，小在前';
COMMENT ON COLUMN a_model_preset.status IS '1上架 2下架';

-- ---------------------------------------------------------------------
-- 模型配置表：运行时生效的模型实例（从预设创建或自建，API KEY AES 加密存储）
-- ---------------------------------------------------------------------
CREATE TABLE a_model (
    id            BIGINT       NOT NULL,
    model_name    VARCHAR(64)  NOT NULL,
    model_type    SMALLINT     NOT NULL,
    provider      VARCHAR(32)  NOT NULL,
    base_url      VARCHAR(255) NOT NULL,
    api_format    SMALLINT     NOT NULL DEFAULT 1,
    auth_type     SMALLINT     NOT NULL DEFAULT 1,
    auth_key_name VARCHAR(64)  NULL,
    api_model_id  VARCHAR(64)  NOT NULL,
    api_key       VARCHAR(512) NOT NULL,
    preset_id     BIGINT       NULL,
    params        VARCHAR(512) NULL,
    status        SMALLINT     NOT NULL DEFAULT 1,
    remark        VARCHAR(255) NULL,
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_type_status ON a_model (model_type, status);

COMMENT ON TABLE a_model IS '模型配置表';
COMMENT ON COLUMN a_model.model_name IS '展示名';
COMMENT ON COLUMN a_model.model_type IS '模型类型：1文字 2向量';
COMMENT ON COLUMN a_model.provider IS '厂商标识';
COMMENT ON COLUMN a_model.base_url IS '上游 API 地址';
COMMENT ON COLUMN a_model.api_format IS '上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic(messages) 4 Gemini(generateContent)';
COMMENT ON COLUMN a_model.auth_type IS '认证方式：1 Bearer头 2 Key头直传 3 查询参数';
COMMENT ON COLUMN a_model.auth_key_name IS '认证字段名';
COMMENT ON COLUMN a_model.api_model_id IS '厂商侧模型 ID';
COMMENT ON COLUMN a_model.api_key IS 'API KEY';
COMMENT ON COLUMN a_model.preset_id IS '来源预设 ID';
COMMENT ON COLUMN a_model.params IS '默认参数';
COMMENT ON COLUMN a_model.status IS '1启用 2停用';

-- ---------------------------------------------------------------------
-- 用户模型表：用户对各模型的使用权与每日配额（实时频率限制走 Redis 滑窗）
-- ---------------------------------------------------------------------
CREATE TABLE a_user_model (
    id          BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    model_id    BIGINT      ,
    status      SMALLINT    NOT NULL DEFAULT 1,
    daily_quota INT         NOT NULL DEFAULT 0,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_model ON a_user_model (user_id, model_id);

COMMENT ON TABLE a_user_model IS '用户模型表';
COMMENT ON COLUMN a_user_model.status IS '状态：1可用 2禁用';
COMMENT ON COLUMN a_user_model.daily_quota IS '每日调用上限(次数)：0=不限';

-- ---------------------------------------------------------------------
-- 知识库表：上传文档的元数据（向量化数据在 Spring AI 的 vector_store 表）
-- ---------------------------------------------------------------------
CREATE TABLE a_kb (
    id          BIGINT       NOT NULL,
    kb_name     VARCHAR(128) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(16)  NULL,
    file_size   BIGINT       NULL,
    chunk_count INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    fail_reason VARCHAR(512) NULL,
    creator_id  BIGINT       NOT NULL,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_kb_status ON a_kb (status);

COMMENT ON TABLE a_kb IS '知识库表';
COMMENT ON COLUMN a_kb.kb_name IS '知识库名称';
COMMENT ON COLUMN a_kb.file_type IS '文件类型：pdf/md/txt/docx';
COMMENT ON COLUMN a_kb.file_size IS '文件大小(字节)';
COMMENT ON COLUMN a_kb.chunk_count IS '切片数量';
COMMENT ON COLUMN a_kb.status IS '状态：1处理中 2可用 3失败';
COMMENT ON COLUMN a_kb.fail_reason IS '失败原因';
COMMENT ON COLUMN a_kb.creator_id IS '上传管理员ID';

-- ---------------------------------------------------------------------
-- 会话表：用户会话元信息（消息在 a_message）
-- ---------------------------------------------------------------------
CREATE TABLE a_conversation (
    id                 BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    title              VARCHAR(128) NOT NULL DEFAULT '新会话',
    chat_model_id      BIGINT       NULL,
    message_count      INT          NOT NULL DEFAULT 0,
    last_message_at    TIMESTAMPTZ  NULL,
    last_interaction_at TIMESTAMPTZ NULL,
    title_status       SMALLINT     NOT NULL DEFAULT 0,
    title_triggered_at TIMESTAMPTZ  NULL,
    summary            TEXT         NULL,
    summary_message_id BIGINT       NULL,
    status             SMALLINT     NOT NULL DEFAULT 1,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_conv_user_time ON a_conversation (user_id, last_message_at);

COMMENT ON TABLE a_conversation IS '会话表';
COMMENT ON COLUMN a_conversation.chat_model_id IS '文字模型ID';
COMMENT ON COLUMN a_conversation.message_count IS '消息数';
COMMENT ON COLUMN a_conversation.last_message_at IS '最近消息时间';
COMMENT ON COLUMN a_conversation.last_interaction_at IS '最近有效用户交互时间（72h翻篇判定锚点；标题/系统任务不刷新）';
COMMENT ON COLUMN a_conversation.title_status IS '标题状态：0未触发 1生成中 2成功 3最终失败（保留占位不补发）';
COMMENT ON COLUMN a_conversation.title_triggered_at IS '标题任务首次触发时间（每会话只触发一次）';
COMMENT ON COLUMN a_conversation.summary IS '当前会话压缩摘要（不跨会话，异步任务写入）';
COMMENT ON COLUMN a_conversation.summary_message_id IS '摘要覆盖到的消息水位（写入前校验不后退）';
COMMENT ON COLUMN a_conversation.status IS '状态：1正常 2归档';

-- ---------------------------------------------------------------------
-- 账户当前会话指针表：一账户一行，指向唯一当前主会话（从未交流为空）。
-- active_request_id 为主回复占用位：同账户同时只允许一个主回复任务，
-- 流结束/失败 CAS 释放；实例崩溃由占用超时（active_since + 阈值）兜底回收。
-- ---------------------------------------------------------------------
CREATE TABLE a_user_chat_state (
    user_id                 BIGINT       NOT NULL,
    current_conversation_id BIGINT       NULL,
    active_request_id       VARCHAR(64)  NULL,
    active_since            TIMESTAMPTZ  NULL,
    version                 INT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id)
);

COMMENT ON TABLE a_user_chat_state IS '账户当前会话指针表（账户级唯一主会话）';
COMMENT ON COLUMN a_user_chat_state.current_conversation_id IS '当前主会话ID；空=从未交流或已翻篇';
COMMENT ON COLUMN a_user_chat_state.active_request_id IS '进行中主回复的 clientRequestId；空=空闲';
COMMENT ON COLUMN a_user_chat_state.active_since IS '占用开始时间（占用超时回收的计时锚点）';
COMMENT ON COLUMN a_user_chat_state.version IS '并发版本（乐观锁预留）';

-- ---------------------------------------------------------------------
-- AI 请求回执表：clientRequestId 幂等锚点，user_id + client_request_id 唯一。
-- 回执在主模型调用前同事务创建；流结束置 SUCCESS（关联 messageId）/ FAILED；
-- 忙拒绝落 REJECTED。占用超时回收时孤儿 PROCESSING 回执置 FAILED。
-- ---------------------------------------------------------------------
CREATE TABLE a_ai_request (
    id                BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    client_request_id VARCHAR(64)  NOT NULL,
    conversation_id   BIGINT       NULL,
    request_status    SMALLINT     NOT NULL,
    message_id        BIGINT       NULL,
    err_msg           VARCHAR(512) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_request_user_client UNIQUE (user_id, client_request_id)
);
CREATE INDEX idx_ai_request_user_time ON a_ai_request (user_id, created_at);

COMMENT ON TABLE a_ai_request IS 'AI 请求回执表（clientRequestId 幂等）';
COMMENT ON COLUMN a_ai_request.client_request_id IS '客户端请求唯一标识，重试须换新值';
COMMENT ON COLUMN a_ai_request.conversation_id IS '关联会话ID';
COMMENT ON COLUMN a_ai_request.request_status IS '状态：1处理中 2成功 3失败 4忙拒绝';
COMMENT ON COLUMN a_ai_request.message_id IS '成功轮消息ID（SUCCESS 时回填）';
COMMENT ON COLUMN a_ai_request.err_msg IS '失败/拒绝原因';

-- ---------------------------------------------------------------------
-- 消息表：一条消息 = 一轮对话（触发输入 → 工具调用 → AI 回复）
-- role = user 用户（消息/卡片）、assistant AI、system 系统自动（如标题生成）；
-- 历史类读取（上下文组装 / 标题素材 / 前端历史）默认排除 system 轮。
-- ---------------------------------------------------------------------
CREATE TABLE a_message (
    id               BIGINT       NOT NULL,
    conversation_id  BIGINT       NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    user_content     TEXT         NULL,
    user_content_masked TEXT      NULL,
    card_id          BIGINT       NULL,
    reply            TEXT         NULL,
    tools            VARCHAR(512) NULL,
    chat_model_id    BIGINT       NULL,
    usage_log_id     BIGINT       NULL,
    refs             VARCHAR(1024) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_msg_conv ON a_message (conversation_id, id);

COMMENT ON TABLE a_message IS '消息表（一条=一轮对话）';
COMMENT ON COLUMN a_message.role IS '触发方：user用户 assistant AI system系统自动（如标题生成）';
COMMENT ON COLUMN a_message.user_content IS '本轮输入：用户原文或卡片 prompt_text；system 轮为空';
COMMENT ON COLUMN a_message.user_content_masked IS '喂模型的净化版（展示与历史接口只回 user_content 原文）；空=回退原文（历史数据原文即当时净化前口径）';
COMMENT ON COLUMN a_message.card_id IS '便捷卡片ID：卡片发起的轮次记录来源';
COMMENT ON COLUMN a_message.reply IS 'AI 回复；NULL=本轮调用失败（用户输入仍保留）';
COMMENT ON COLUMN a_message.tools IS '本轮调用的工具清单（JSON 数组字符串，板块五前恒空）';
COMMENT ON COLUMN a_message.chat_model_id IS '文字模型ID';
COMMENT ON COLUMN a_message.usage_log_id IS '关联用量流水ID（a_usage_log.id，token 用量记录在用量表）';
COMMENT ON COLUMN a_message.refs IS 'RAG引用溯源';

-- ---------------------------------------------------------------------
-- 便捷卡片表：聊天输入框上方的快捷提问卡片（点击即发送固定提示词）
-- ---------------------------------------------------------------------
CREATE TABLE a_quick_card (
    id          BIGINT       NOT NULL,
    card_name   VARCHAR(32)  NOT NULL,
    prompt_text VARCHAR(512) NOT NULL,
    icon        VARCHAR(64)  NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    start_at    TIMESTAMPTZ  NULL,
    end_at      TIMESTAMPTZ  NULL,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_card_status_sort ON a_quick_card (status, sort_no);

COMMENT ON TABLE a_quick_card IS '便捷卡片表';
COMMENT ON COLUMN a_quick_card.card_name IS '卡片标题';
COMMENT ON COLUMN a_quick_card.prompt_text IS '点击发送的提示词';
COMMENT ON COLUMN a_quick_card.icon IS '图标标识';
COMMENT ON COLUMN a_quick_card.sort_no IS '展示排序，小在前';
COMMENT ON COLUMN a_quick_card.status IS '状态：1上架 2下架';
COMMENT ON COLUMN a_quick_card.start_at IS '活动开始(空=常驻)';
COMMENT ON COLUMN a_quick_card.end_at IS '活动结束(空=常驻)';

-- ---------------------------------------------------------------------
-- 用量记录表：模型调用流水（配额扣减与管理端调用统计的数据源，不可变）
-- ---------------------------------------------------------------------
CREATE TABLE a_usage_log (
    id               BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    model_id         BIGINT      NOT NULL,
    conversation_id  BIGINT      NULL,
    input_tokens     INT         NULL,
    cached_tokens    INT         NULL,
    output_tokens    INT         NULL,
    latency_ms       INT         NULL,
    call_status      SMALLINT    NOT NULL,
    err_msg          VARCHAR(512) NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_usage_user_time ON a_usage_log (user_id, created_at);
CREATE INDEX idx_usage_model_time ON a_usage_log (model_id, created_at);

COMMENT ON TABLE a_usage_log IS '用量记录表';
COMMENT ON COLUMN a_usage_log.model_id IS '调用的模型 ID';
COMMENT ON COLUMN a_usage_log.conversation_id IS '关联会话ID';
COMMENT ON COLUMN a_usage_log.input_tokens IS '新增输入token数（未命中缓存的输入）';
COMMENT ON COLUMN a_usage_log.cached_tokens IS '缓存命中token数';
COMMENT ON COLUMN a_usage_log.output_tokens IS '输出token数';
COMMENT ON COLUMN a_usage_log.latency_ms IS '调用耗时（毫秒）';
COMMENT ON COLUMN a_usage_log.call_status IS '调用结果：1成功 2失败';
COMMENT ON COLUMN a_usage_log.err_msg IS '失败原因';

-- ---------------------------------------------------------------------
-- AI 写操作表：购票/退票 预检-确认-执行 的状态机行（2.0 服务 Tool）
-- 预检创建 PENDING；确认轮 CAS 消费转 EXECUTING；终态 SUCCESS/FAILED/UNKNOWN/CANCELLED/EXPIRED。
-- 本表是确认有效性的权威，Redis 待确认单仅为镜像。
-- ---------------------------------------------------------------------
CREATE TABLE a_ai_operation (
    id                  BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    conversation_id     BIGINT        NOT NULL,
    type                SMALLINT      NOT NULL,
    confirm_id          VARCHAR(64)   NOT NULL,
    summary_text        VARCHAR(1024) NOT NULL,
    summary_snapshot    VARCHAR(2048) NOT NULL,
    summary_version     INT           NOT NULL DEFAULT 1,
    idempotency_key     VARCHAR(64)   NOT NULL,
    status              SMALLINT      NOT NULL,
    biz_no              VARCHAR(64)   NULL,
    error_code          VARCHAR(32)   NULL,
    consumed_at         TIMESTAMPTZ   NULL,
    expires_at          TIMESTAMPTZ   NOT NULL,
    execution_started_at TIMESTAMPTZ  NULL,
    execution_lease_until TIMESTAMPTZ NULL,
    recovery_attempts   INT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_operation_idem UNIQUE (idempotency_key)
);
CREATE INDEX idx_ai_operation_user_status ON a_ai_operation (user_id, status);
CREATE INDEX idx_ai_operation_conv ON a_ai_operation (conversation_id);

COMMENT ON TABLE a_ai_operation IS 'AI写操作表（购票/退票确认协议）';
COMMENT ON COLUMN a_ai_operation.type IS '操作类型：1购票 2退票';
COMMENT ON COLUMN a_ai_operation.confirm_id IS '确认摘要引用（不可猜测但非密钥，明文保存支持换设备恢复；日志不打印）';
COMMENT ON COLUMN a_ai_operation.summary_text IS '服务端模板渲染的确认摘要文本（模型不参与定价）';
COMMENT ON COLUMN a_ai_operation.summary_snapshot IS '摘要业务快照JSON（执行复检与幂等复核的比对基准）';
COMMENT ON COLUMN a_ai_operation.summary_version IS '摘要版本（防旧confirmId确认新摘要）';
COMMENT ON COLUMN a_ai_operation.idempotency_key IS '业务幂等键（=operationId，member写接口按此去重）';
COMMENT ON COLUMN a_ai_operation.status IS '状态：0待确认 1执行中 2成功 3失败 4结果不明 5已取消 6已过期';
COMMENT ON COLUMN a_ai_operation.biz_no IS '业务单号（订单号/退款批次号）';
COMMENT ON COLUMN a_ai_operation.consumed_at IS '确认令牌消费时间（CAS成功即写入）';
COMMENT ON COLUMN a_ai_operation.expires_at IS '确认摘要失效时间（默认5分钟，独立计时）';
COMMENT ON COLUMN a_ai_operation.execution_lease_until IS '执行租约到期时间（崩溃后恢复任务据此回收）';
COMMENT ON COLUMN a_ai_operation.recovery_attempts IS '恢复任务已尝试次数';
-- ---------------------------------------------------------------------
-- 账户长期记忆表：跨会话保留的稳定低敏感偏好（真相源 PostgreSQL，随修改/删除/清空变更）。
-- SINGLE 单值键同 key 只一行（dedupe_key=key）；MULTI 按内容指纹去重（dedupe_key=内容SHA-256）。
-- ---------------------------------------------------------------------
CREATE TABLE a_user_memory (
    id                 BIGINT        NOT NULL,
    user_id            BIGINT        NOT NULL,
    memory_key         VARCHAR(64)   NOT NULL,
    value_mode         SMALLINT      NOT NULL DEFAULT 1,
    dedupe_key         VARCHAR(80)   NOT NULL,
    category           VARCHAR(32)   NOT NULL,
    content            VARCHAR(200)  NOT NULL,
    source_type        VARCHAR(16)   NOT NULL,
    source_message_id  BIGINT        NULL,
    confidence         NUMERIC(3,2)  NOT NULL DEFAULT 1.00,
    status             SMALLINT      NOT NULL DEFAULT 1,
    version            INT           NOT NULL DEFAULT 0,
    last_confirmed_at  TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_user_memory_user ON a_user_memory (user_id, status, category);
CREATE UNIQUE INDEX uk_user_memory_dedupe ON a_user_memory (user_id, memory_key, dedupe_key);

COMMENT ON TABLE a_user_memory IS '账户长期记忆表（属于账户不属于会话，72h轮换不清除）';
COMMENT ON COLUMN a_user_memory.memory_key IS '规范键，如 seat_preference/departure_city/response_style';
COMMENT ON COLUMN a_user_memory.value_mode IS '取值模式：1单值SINGLE 2多条MULTI';
COMMENT ON COLUMN a_user_memory.dedupe_key IS '去重键：SINGLE=memory_key；MULTI=规范化内容SHA-256';
COMMENT ON COLUMN a_user_memory.category IS '分类：TRAVEL_PREFERENCE/COMMUNICATION_PREFERENCE/STABLE_CONSTRAINT/USER_FACT';
COMMENT ON COLUMN a_user_memory.content IS '单条简洁正文（长度受限）';
COMMENT ON COLUMN a_user_memory.source_type IS '来源：USER_EXPLICIT对话显式/USER_MANUAL手动/AUTO_EXTRACTED提炼';
COMMENT ON COLUMN a_user_memory.confidence IS '置信度0~1，显式用户指令为1';
COMMENT ON COLUMN a_user_memory.status IS '状态：1启用 2停用';
COMMENT ON COLUMN a_user_memory.version IS '乐观锁版本，异步任务防旧写';
COMMENT ON COLUMN a_user_memory.last_confirmed_at IS '最近用户确认或系统采纳时间';

-- ---------------------------------------------------------------------
-- 记忆设置表：总开关 + 单调递增纪元（删除/清空/关闭使旧异步提炼结果失效防复活）。
-- ---------------------------------------------------------------------
CREATE TABLE a_user_memory_setting (
    user_id       BIGINT      NOT NULL,
    enabled       SMALLINT    NOT NULL DEFAULT 1,
    version       INT         NOT NULL DEFAULT 0,
    memory_epoch  INT         NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id)
);

COMMENT ON TABLE a_user_memory_setting IS '账户记忆设置表（总开关默认开启）';
COMMENT ON COLUMN a_user_memory_setting.enabled IS '总开关：1开启 2关闭（关闭不提炼不注入，已有内容保留）';
COMMENT ON COLUMN a_user_memory_setting.memory_epoch IS '记忆纪元：删除/清空/关闭重置时递增，旧提炼任务比对后放弃';

-- ---------------------------------------------------------------------
-- 记忆审计表：只记操作要素，不保存被删正文（删除后不可从审计恢复内容）。
-- ---------------------------------------------------------------------
CREATE TABLE a_user_memory_audit (
    id            BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    operation     VARCHAR(32) NOT NULL,
    memory_id     BIGINT      NULL,
    category      VARCHAR(32) NULL,
    operator_type VARCHAR(16) NOT NULL,
    request_id    VARCHAR(64) NULL,
    result_code   VARCHAR(32) NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_user_memory_audit ON a_user_memory_audit (user_id, created_at);

COMMENT ON TABLE a_user_memory_audit IS '记忆操作审计表（追加写）';
COMMENT ON COLUMN a_user_memory_audit.operation IS '操作：CREATE/UPDATE/ENABLE/DISABLE/DELETE/CLEAR/SETTING/EXTRACTION_ACCEPT/EXTRACTION_REJECT';

-- ---------------------------------------------------------------------
-- 记忆任务 Outbox 表：主回复消息与任务行同事务提交，Relay 投递 MQ，防丢防重。
-- ---------------------------------------------------------------------
CREATE TABLE a_memory_task_outbox (
    id               BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    conversation_id  BIGINT        NOT NULL,
    request_id       VARCHAR(64)   NOT NULL,
    task_type        VARCHAR(32)   NOT NULL,
    memory_epoch     INT           NULL,
    payload          VARCHAR(1024) NOT NULL,
    status           SMALLINT      NOT NULL DEFAULT 0,
    attempts         INT           NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMPTZ   NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_memory_outbox ON a_memory_task_outbox (user_id, request_id, task_type);
CREATE INDEX idx_memory_outbox_status ON a_memory_task_outbox (status, next_retry_at);

COMMENT ON TABLE a_memory_task_outbox IS '记忆任务Outbox表（提炼/摘要，与消息落库同事务）';
COMMENT ON COLUMN a_memory_task_outbox.task_type IS '任务类型：MEMORY_EXTRACTION提炼/CONVERSATION_SUMMARY会话摘要';
COMMENT ON COLUMN a_memory_task_outbox.memory_epoch IS '入队时记忆纪元（仅提炼任务），消费前比对防复活';
COMMENT ON COLUMN a_memory_task_outbox.payload IS '最小任务数据JSON（消息id/摘要水位），消费端回查已落库内容';
COMMENT ON COLUMN a_memory_task_outbox.status IS '状态：0待投递 1已投递 2已消费 3失败';

package com.weiyi.agent.server.constant;

/**
 * agent 域常量。
 *
 * <p>状态约定与 common 的 {@code Constants.STATUS_ENABLED/DISABLED(1/0)} 不同：
 * agent 域 DDL（真相源 sql/agent/01_schema.sql）统一 1=上架/启用、2=下架/停用。
 */
public final class AgentConstants {

    /** 上架 / 启用 */
    public static final int STATUS_ON = 1;

    /** 下架 / 停用 */
    public static final int STATUS_OFF = 2;

    /** 模型类型：文字（chat） */
    public static final int MODEL_TYPE_CHAT = 1;

    /** 模型类型：向量（embedding） */
    public static final int MODEL_TYPE_EMBEDDING = 2;

    /** 上游格式 */
    public static final int API_FORMAT_OPENAI_CHAT = 1;
    public static final int API_FORMAT_OPENAI_RESPONSES = 2;
    public static final int API_FORMAT_ANTHROPIC = 3;
    public static final int API_FORMAT_GEMINI = 4;

    /** 认证方式：Bearer 头（auth_key_name 固定，不落库） */
    public static final int AUTH_BEARER = 1;

    /** 认证方式：Key 头直传（auth_key_name 必填，如 x-api-key） */
    public static final int AUTH_HEADER = 2;

    /** 认证方式：查询参数（auth_key_name 必填，参数名） */
    public static final int AUTH_QUERY = 3;

    /** 调用结果：成功 */
    public static final int CALL_STATUS_SUCCESS = 1;

    /** 调用结果：失败 */
    public static final int CALL_STATUS_FAIL = 2;

    /** 会话标题占位值（DDL a_conversation.title DEFAULT，标题判定与 CAS 落库的锚点） */
    public static final String CONVERSATION_TITLE_PLACEHOLDER = "新会话";

    /** 标题状态（a_conversation.title_status）：未触发 */
    public static final int TITLE_STATUS_NONE = 0;

    /** 标题状态：生成中（首轮已触发） */
    public static final int TITLE_STATUS_GENERATING = 1;

    /** 标题状态：成功 */
    public static final int TITLE_STATUS_SUCCESS = 2;

    /** 标题状态：最终失败（保留占位，不补发，可手动重新生成） */
    public static final int TITLE_STATUS_FAILED = 3;

    /** AI 请求回执状态（a_ai_request.request_status）：处理中 */
    public static final int AI_REQUEST_PROCESSING = 1;

    /** AI 请求回执状态：成功 */
    public static final int AI_REQUEST_SUCCESS = 2;

    /** AI 请求回执状态：失败 */
    public static final int AI_REQUEST_FAILED = 3;

    /** AI 请求回执状态：忙拒绝（已有主回复任务占用） */
    public static final int AI_REQUEST_REJECTED = 4;

    /** 标题状态数值转对外字符串（SSE title/meta/done 与 current 出参口径） */
    public static String titleStatusName(Integer status) {
        if (status == null) {
            return "NONE";
        }
        return switch (status) {
            case TITLE_STATUS_GENERATING -> "GENERATING";
            case TITLE_STATUS_SUCCESS -> "SUCCESS";
            case TITLE_STATUS_FAILED -> "FAILED";
            default -> "NONE";
        };
    }

    /** 消息角色（a_message.role，本轮触发方）：用户（消息/卡片） */
    public static final String MESSAGE_ROLE_USER = "user";

    /** 消息角色：AI */
    public static final String MESSAGE_ROLE_ASSISTANT = "assistant";

    /** 消息角色：系统自动触发（如标题生成） */
    public static final String MESSAGE_ROLE_SYSTEM = "system";

    /** 写操作类型（a_ai_operation.type）：购票 */
    public static final int OPERATION_TYPE_BUY = 1;

    /** 写操作类型：退票 */
    public static final int OPERATION_TYPE_REFUND = 2;

    /** 写操作状态（a_ai_operation.status）：待确认 */
    public static final int OPERATION_STATUS_PENDING = 0;

    /** 写操作状态：执行中（确认令牌已消费） */
    public static final int OPERATION_STATUS_EXECUTING = 1;

    /** 写操作状态：成功 */
    public static final int OPERATION_STATUS_SUCCESS = 2;

    /** 写操作状态：失败（业务明确拒绝） */
    public static final int OPERATION_STATUS_FAILED = 3;

    /** 写操作状态：结果不明（等待对账） */
    public static final int OPERATION_STATUS_UNKNOWN = 4;

    /** 写操作状态：已取消（用户取消/新预检覆盖/会话翻篇等） */
    public static final int OPERATION_STATUS_CANCELLED = 5;

    /** 写操作状态：已过期（确认窗口超时） */
    public static final int OPERATION_STATUS_EXPIRED = 6;

    /** 记忆取值模式（a_user_memory.value_mode）：单值键同 key 覆盖 */
    public static final int MEMORY_MODE_SINGLE = 1;

    /** 记忆取值模式：多条并存，按内容指纹去重 */
    public static final int MEMORY_MODE_MULTI = 2;

    /** 记忆分类 */
    public static final String MEMORY_CATEGORY_TRAVEL = "TRAVEL_PREFERENCE";
    public static final String MEMORY_CATEGORY_COMMUNICATION = "COMMUNICATION_PREFERENCE";
    public static final String MEMORY_CATEGORY_CONSTRAINT = "STABLE_CONSTRAINT";
    public static final String MEMORY_CATEGORY_USER_FACT = "USER_FACT";

    /** 记忆来源：对话内显式指令（"记住……"） */
    public static final String MEMORY_SOURCE_EXPLICIT = "USER_EXPLICIT";

    /** 记忆来源：记忆管理页手动新增/修改 */
    public static final String MEMORY_SOURCE_MANUAL = "USER_MANUAL";

    /** 记忆来源：异步提炼候选被采纳 */
    public static final String MEMORY_SOURCE_EXTRACTED = "AUTO_EXTRACTED";

    /** 记忆任务类型（a_memory_task_outbox.task_type）：长期记忆提炼 */
    public static final String MEMORY_TASK_EXTRACTION = "MEMORY_EXTRACTION";

    /** 记忆任务类型：会话摘要压缩 */
    public static final String MEMORY_TASK_SUMMARY = "CONVERSATION_SUMMARY";

    /** 记忆任务状态（a_memory_task_outbox.status）：待投递 */
    public static final int MEMORY_TASK_PENDING = 0;

    /** 记忆任务状态：已投递 MQ */
    public static final int MEMORY_TASK_SENT = 1;

    /** 记忆任务状态：已消费完成 */
    public static final int MEMORY_TASK_CONSUMED = 2;

    /** 记忆任务状态：最终失败（放弃） */
    public static final int MEMORY_TASK_FAILED = 3;

    /** FSM 顶层状态（工作记忆 state 字段）：空闲，执行顶层意图路由 */
    public static final String FSM_IDLE = "IDLE";

    /** FSM：普通交流/兜底分支 */
    public static final String FSM_CHAT = "CHAT";

    /** FSM：查票补槽与查询分支 */
    public static final String FSM_QUERY = "QUERY_FILLING";

    /** FSM：购票分支 */
    public static final String FSM_BUY = "BUY_AGENT";

    /** FSM：退票分支 */
    public static final String FSM_REFUND = "REFUND_AGENT";

    /** 意图路由枚举值（intent-router 输出）：普通交流 */
    public static final String INTENT_CHAT = "chat";

    /** 意图：查票 */
    public static final String INTENT_QUERY = "ticket_query";

    /** 意图：购票 */
    public static final String INTENT_BUY = "ticket_buy";

    /** 意图：退票 */
    public static final String INTENT_REFUND = "ticket_refund";

    /** 意图：知识问答（知识库未接入时服务端降级 chat） */
    public static final String INTENT_RAG = "rag_question";

    /** 意图：低置信澄清（保持 IDLE） */
    public static final String INTENT_CLARIFY = "clarify";

    /** 切换判定枚举值：继续当前任务 */
    public static final String SWITCH_CONTINUE = "CONTINUE";

    /** 切换判定：开启新任务 */
    public static final String SWITCH_SWITCH = "SWITCH";

    /** 切换判定：临时旁支提问（仅查票任务有效） */
    public static final String SWITCH_SIDE = "SIDE";

    /** 连续失败轮阈值：达到强制回 IDLE 并清待确认状态 */
    public static final int FSM_FAIL_LIMIT = 2;

    private AgentConstants() {
    }
}

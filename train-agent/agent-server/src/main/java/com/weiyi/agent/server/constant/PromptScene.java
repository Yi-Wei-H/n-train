package com.weiyi.agent.server.constant;

/**
 * 提示词场景：与 resources/prompts/ 下 {scene}-system.st / {scene}-user.st 模板对一一对应，
 * 场景名即模板文件名前缀，禁止同场景出现多个隐式默认文件。
 */
public enum PromptScene {

    /** IDLE 顶层意图路由判定（结构化输出） */
    INTENT_ROUTER("intent-router"),

    /** 非 IDLE 状态的任务切换判定（结构化输出） */
    TASK_SWITCH("task-switch"),

    /** 低置信澄清引导 */
    CLARIFY("clarify"),

    /** 普通交流兜底分支 */
    CHAT("chat"),

    /** 查票补槽与查询分支 */
    QUERY("query"),

    /** 购票分支（预检—确认—服务端执行） */
    BUY("buy"),

    /** 退票分支（预检—确认—服务端执行） */
    REFUND("refund"),

    /** 首次标题生成（仅凭首条输入快照） */
    TITLE_NAMING("title-naming"),

    /** 长期记忆提炼（结构化输出） */
    MEMORY_EXTRACTION("memory-extraction"),

    /** 会话摘要压缩 */
    CONVERSATION_SUMMARY("conversation-summary"),

    /** 知识问答分支（知识库检索接入后启用） */
    RAG("rag");

    private final String key;

    PromptScene(String key) {
        this.key = key;
    }

    /** 模板文件名前缀 */
    public String key() {
        return key;
    }
}

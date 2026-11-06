package com.weiyi.agent.server.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * a_model.params 参数快照的载体
 * <p>字段集是三种上游格式请求参数的全集：解析时全量接收，实际生效看各构建方法
 */
@Data
public class ModelParams {

    // ---------- 多上游通用 ----------

    /** 采样温度：OpenAI 0~2，Anthropic 0~1 */
    private Double temperature;

    /** 核采样阈值 0~1，按累计概率截断候选词池，为 1 --关闭核采样，允许全部 token 参与采样 */
    private Double topP;

    /** 最大输出 token 数：OpenAI 映射 max_tokens，Anthropic 映射 max_tokens（其必填项） */
    private Integer maxTokens;

    /** 停止序列：OpenAI 映射 stop，Anthropic 映射 stop_sequences */
    private List<String> stopSequences;

    // ---------- OpenAI 系（chat / Responses）特有 ----------

    /** 频率惩罚 -2~2，抑制已出现 token 的重复 */
    private Double frequencyPenalty;

    /** 存在惩罚 -2~2，鼓励引入新话题 */
    private Double presencePenalty;

    /** 随机种子，尽力确定性采样 */
    private Integer seed;

    /** 是否允许并行工具调用 */
    private Boolean parallelToolCalls;

    /** 回答详细程度（low/medium/high，Responses 及 o 系） */
    private String verbosity;

    /** 推理力度（low/medium/high，o 系推理模型与 Responses） */
    private String reasoningEffort;

    /** o 系推理模型的最大输出 token 数（chat 映射 max_completion_tokens，Responses 映射 max_output_tokens），设置后优先于 maxTokens */
    private Integer maxCompletionTokens;

    // ---------- Anthropic 特有 ----------

    /** 前 K 个 token 内采样（Anthropic 特有，OpenAI 系不使用） */
    private Integer topK;

    /** 扩展思考预算（映射 thinking.budget_tokens，单位 token，Anthropic 要求 ≥1024） */
    private Integer thinkingBudgetTokens;

    /**
     * 思考开关（业界形态 {"thinking":{"type":"enabled/disabled"}}）：
     * Anthropic 格式一等映射——"disabled" 关闭思考，"enabled" 开启（必须同时配 thinkingBudgetTokens）；
     * OpenAI 兼容厂商（智谱等）的思考开关是各厂非标键，不在此映射，用 extraBody 按厂商文档透传。
     */
    private String thinkingType;

    /**
     * 请求体顶层透传（仅 OpenAI 系生效）：原样并入 chat/completions 请求 JSON，
     * 用于各兼容厂商的非标参数（如 {"thinking":{"type":"disabled"}}、enable_thinking、
     * 非标 reasoning_effort 取值等），键名与取值由配置人按厂商文档自行保证。
     */
    private Map<String, Object> extraBody;

    /**
     * 剥离开头思考块：模型拒绝/不支持关闭参数且思考以 <think>…</think> 包裹混在正文里时置 true，
     * 服务端流式剥离后仅下发与落库正文（仅处理回复开头的中括块，中途出现的标签不动）。
     */
    private Boolean stripThinking = Boolean.TRUE;

    /** 是否禁用并行工具调用（与 OpenAI 系的 parallelToolCalls 反义，构建时各自映射） */
    private Boolean disableParallelToolUse;
}

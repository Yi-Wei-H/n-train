package com.weiyi.agent.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "ntrain.agent")
public class AgentProperties {

    // 默认温度
    private Double temperature = 0.2;

    // Anthropic 必填 max_tokens 的兜底值
    private Integer defaultAnthropicMaxTokens = 4096;

    // 向量维度
    private Integer dimensions = 1024;

    // 记忆中历史消息数量
    private Integer messageCount = 20;

    // 会话工作记忆（FSM 状态/工具引用/待确认单镜像）：TTL 分钟数，随读写滑动续期
    private Memory memory = new Memory();

    // 写操作确认摘要有效期（分钟），独立计时，不随工作记忆 TTL 续期延长
    private Confirm confirm = new Confirm();

    // 会话生命周期：72h 翻篇与主回复占用回收
    private Conversation conversation = new Conversation();

    // 主回复流：SSE 连接超时
    private Chat chat = new Chat();

    // 输入与内容安全：高危拒答词、SafeGuard 敏感词、输入上限
    private Security security = new Security();

    // 意图路由：置信阈值与路由上下文轮数
    private Router router = new Router();

    /**
     * 提示词模板路径覆盖：key = 场景-角色（如 intent-router-system、chat-user），
     * 缺省 classpath:prompts/&lt;key&gt;.st；Nacos 只可调整路径，模板正文随应用发布。
     */
    private Map<String, String> prompts = new LinkedHashMap<>();

    @Data
    public static class Memory {

        /** 工作记忆 TTL（分钟），过期整体失效、FSM 回 IDLE */
        private Integer workTtlMinutes = 30;

        /** 单轮注入长期记忆最大条数（超预算保留显式且高相关项） */
        private Integer maxInjectedItems = 20;

        /** 记忆正文最大长度（字符） */
        private Integer maxContentLength = 200;

        /** 会话摘要触发阈值：未压缩消息轮数超过该值入队摘要任务 */
        private Integer summaryThresholdRounds = 20;

        /** 摘要输出最大长度（字符） */
        private Integer summaryMaxLength = 1000;

        /** 结构化输出总尝试次数（首次 + 修复重试） */
        private Integer structuredMaxAttempts = 2;

        /** 自动提炼候选采纳的最低置信度（低于不落库） */
        private Double extractionMinConfidence = 0.8;
    }

    @Data
    public static class Confirm {

        /** 确认摘要有效期（分钟） */
        private Integer ttlMinutes = 5;
    }

    @Data
    public static class Conversation {

        /** 空闲翻篇阈值（小时）：距上次有效交互超过该值翻篇旧会话（打开页面惰性判定 + 发送兜底判定） */
        private Integer idleTimeoutHours = 72;

        /** 主回复占用超时（秒）：超过视为孤儿占用回收，允许新请求（实例崩溃兜底） */
        private Integer busyExpireSeconds = 180;

        /** 首轮标题流内探测周期（毫秒） */
        private Integer titleProbeIntervalMillis = 2500;

        /** 首轮标题流内探测最长持续（毫秒），超时停测由 done/前端轮询兜底 */
        private Integer titleProbeMaxMillis = 30000;
    }

    @Data
    public static class Chat {

        /** 主回复 SSE 流超时（毫秒）：思考型模型长回复超过一分钟属常态；超时只关连接，落库与回执仍按流回调收尾 */
        private Long sseTimeoutMillis = 180_000L;
    }

    @Data
    public static class Router {

        /** 路由置信阈值：低于该值按 clarify 澄清 */
        private Double confidenceThreshold = 0.6;

        /** 路由 user 模板喂入的近期消息轮数（上下文最小化，不喂全量历史） */
        private Integer routerMessageCount = 6;
    }

    @Data
    public static class Security {

        /** 高危/敏感默认词表：入口拒答与模型层兜底共用同一份，安全评审只维护这一处（Nacos 可分别覆盖） */
        public static final List<String> DEFAULT_RISK_WORDS = List.of(
                "赌博", "色情", "暴恐", "枪支买卖", "毒品", "洗钱", "诈骗教程");

        /** 高危拒答词：命中直接拒绝，不建会话不调主模型 */
        private List<String> highRiskWords = DEFAULT_RISK_WORDS;

        /** SafeGuardAdvisor 敏感词：命中返回固定安全回复（不回放历史的模型调用输入兜底） */
        private List<String> sensitiveWords = DEFAULT_RISK_WORDS;

        /** SafeGuard 命中后的固定安全回复 */
        private String safeguardResponse = "抱歉，我只能协助车票与出行相关的请求。";

        /** 单条用户输入最大长度（字符） */
        private Integer maxInputLength = 2000;

        /** AI 接口请求体最大字节数 */
        private Integer maxRequestBodyBytes = 32768;
    }

}

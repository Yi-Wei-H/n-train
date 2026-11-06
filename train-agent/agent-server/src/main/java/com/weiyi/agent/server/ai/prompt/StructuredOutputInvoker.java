package com.weiyi.agent.server.ai.prompt;

import com.weiyi.agent.server.ai.provider.ChatTexts;
import com.weiyi.agent.server.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 结构化输出统一调用器：schema 由 BeanOutputConverter 生成并追加到 system（模板不手写 schema），
 * 解析失败追加严格 JSON 指令做一次修复重试（错误清洗截断后回注），最终失败返回业务指定的降级值。
 * 安全常量由调用方经 PromptAssembler 装配进 systemPrompt，此处只负责 schema 与解析重试。
 */
@Slf4j
@Component
public class StructuredOutputInvoker {

    /** 修复重试时追加的严格 JSON 指令 */
    private static final String STRICT_JSON_INSTRUCTION = """

            # 输出格式要求
            只输出一个合法 JSON 对象，不使用 Markdown 代码块，不输出任何解释或多余文本，所有字符串正确转义。""";

    /** 回注重试的上次错误最大长度 */
    private static final int ERROR_FEEDBACK_LIMIT = 200;

    /** 失败日志中模型原始输出的采样长度（换行压平，便于单行检索） */
    private static final int RAW_SAMPLE_LIMIT = 160;

    private final AgentProperties agentProperties;

    public StructuredOutputInvoker(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 调用并转换为强类型结果。网络/上游异常直接上抛（不重试，避免把基础设施故障当成解析失败
     * 再烧一次模型调用）；仅结构化解析失败才追加严格 JSON 指令做修复重试。
     *
     * @param chatClient   模型客户端（不回放历史的调用，SafeGuard 生效）
     * @param systemPrompt 已含安全常量的 system 文本
     * @param userPrompt   user 模板渲染结果
     * @param converter    目标 Bean 的转换器（schema 来源）
     * @param fallback     最终失败的降级值（路由→chat 兜底、提炼→空结果等）
     * @param scene        场景名（指标与日志标识）
     */
    public <T> T invoke(ChatClient chatClient, String systemPrompt, String userPrompt,
                        StructuredOutputConverter<T> converter, T fallback, String scene) {
        String schema = converter.getFormat();
        int maxAttempts = agentProperties.getMemory().getStructuredMaxAttempts();
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String system = systemPrompt + "\n\n" + schema;
            if (lastError != null) {
                system += STRICT_JSON_INSTRUCTION + "\n上次输出解析失败原因（截断）：" + truncate(lastError);
            }
            // 先取原始文本：上游调用异常在这里直接抛出，不进入解析重试；
            // 思考块在响应里独立成 Generation，取正文而非首个 Generation
            String content = ChatTexts.text(chatClient.prompt()
                    .system(system)
                    .user(userPrompt)
                    .call()
                    .chatResponse());
            try {
                T result = converter.convert(extractJson(content));
                if (attempt > 1) {
                    log.info("[structured] {} 第 {} 次尝试解析成功", scene, attempt);
                }
                return result;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[structured] {} 第 {} 次解析失败：{}（目标类型={}，模型原始输出开头：{}）",
                        scene, attempt, lastError, targetType(converter), rawSample(content));
            }
        }
        log.warn("[structured] {} 最终失败，使用降级结果", scene);
        return fallback;
    }

    /**
     * 容错提取：模型常在 JSON 前后附加解释文字或 Markdown 围栏（如"用户问的是…\n{…}"），
     * 整段解析在首个非法 token 即失败——截取首个 '{' 到最后一个 '}' 再交解析，
     * 同时覆盖围栏形态（```json {…} ``` 的花括号外内容一并剥掉）；
     * 无花括号时原样返回，保持既有报错语义走严格重试。
     */
    private String extractJson(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        return start >= 0 && end > start ? content.substring(start, end + 1) : content;
    }

    private String truncate(String error) {
        return error.length() > ERROR_FEEDBACK_LIMIT ? error.substring(0, ERROR_FEEDBACK_LIMIT) : error;
    }

    /** 解析目标类型（转换器实现类名，场景 scene 共同定位目标 Bean） */
    private String targetType(StructuredOutputConverter<?> converter) {
        return converter.getClass().getSimpleName();
    }

    /** 模型原始输出采样：压平换行后截断，失败时能直接看出输出了什么（前缀文字/围栏/思考过程） */
    private String rawSample(String content) {
        if (content == null) {
            return "(null)";
        }
        String flat = content.replace('\n', ' ');
        return flat.length() <= RAW_SAMPLE_LIMIT ? flat : flat.substring(0, RAW_SAMPLE_LIMIT) + "…";
    }
}

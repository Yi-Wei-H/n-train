package com.weiyi.agent.server.ai.prompt;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.PromptScene;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 提示词模板启动加载与持有：启动期一次性读取全部场景模板并构造 PromptTemplate 单例复用，
 * 模板缺失、不可读或渲染冒烟失败即启动失败，不做运行时降级与热更新，正文只随应用发布变更。
 * 模板路径可经 Nacos（ntrain.agent.prompts，key=场景-角色）覆盖，缺省 classpath:prompts/。
 */
@Component
public class PromptTemplates {

    /** 渲染冒烟样例值：覆盖全部场景占位符的并集（多余键值在渲染时被忽略），新增占位符需同步补充 */
    private static final Map<String, Object> SMOKE_VARS = Map.ofEntries(
            Map.entry("sessionState", "冒烟"),
            Map.entry("currentState", "冒烟"),
            Map.entry("taskSummary", "冒烟"),
            Map.entry("taskState", "冒烟"),
            Map.entry("currentDate", "冒烟"),
            Map.entry("longTermMemory", "冒烟"),
            Map.entry("conversationSummary", "冒烟"),
            Map.entry("historyMessages", "冒烟"),
            Map.entry("userContent", "冒烟"),
            Map.entry("cardData", "冒烟"),
            Map.entry("firstInput", "冒烟"),
            Map.entry("roundContent", "冒烟"),
            Map.entry("previousSummary", "冒烟"),
            Map.entry("knowledgeSegments", "冒烟"));

    private final AgentProperties agentProperties;
    private final EnumMap<PromptScene, PromptTemplate> systemTemplates = new EnumMap<>(PromptScene.class);
    private final EnumMap<PromptScene, PromptTemplate> userTemplates = new EnumMap<>(PromptScene.class);

    public PromptTemplates(AgentProperties agentProperties, ResourceLoader resourceLoader) {
        this.agentProperties = agentProperties;
        for (PromptScene scene : PromptScene.values()) {
            systemTemplates.put(scene, load(templatePath(scene, true), resourceLoader));
            userTemplates.put(scene, load(templatePath(scene, false), resourceLoader));
        }
    }

    public PromptTemplate system(PromptScene scene) {
        return systemTemplates.get(scene);
    }

    public PromptTemplate user(PromptScene scene) {
        return userTemplates.get(scene);
    }

    private String templatePath(PromptScene scene, boolean system) {
        String key = scene.key() + (system ? "-system" : "-user");
        return agentProperties.getPrompts().getOrDefault(key, "classpath:prompts/" + key + ".st");
    }

    /** 读取 + 构造 + 冒烟渲染，任一步失败直接终止启动 */
    private PromptTemplate load(String path, ResourceLoader resourceLoader) {
        try {
            PromptTemplate template = new PromptTemplate(
                    resourceLoader.getResource(path).getContentAsString(StandardCharsets.UTF_8));
            // 渲染结果残留 { } 即有占位符未被样例值覆盖（新增占位符须同步补进 SMOKE_VARS）
            String rendered = template.render(SMOKE_VARS);
            if (rendered.contains("{") || rendered.contains("}")) {
                throw new IllegalStateException("占位符未被冒烟样例值覆盖：" + path);
            }
            return template;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("提示词模板加载或冒烟失败：" + path, e);
        }
    }
}

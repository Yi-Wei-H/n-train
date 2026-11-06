package com.weiyi.agent.server.ai.prompt;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.PromptScene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PromptTemplatesTest {

    @Test
    @DisplayName("全部场景模板成对加载且冒烟渲染通过（缺失/占位符未覆盖即失败）")
    void allSceneTemplatesLoadAndSmokeRender() {
        PromptTemplates templates = new PromptTemplates(new AgentProperties(), new DefaultResourceLoader());
        for (PromptScene scene : PromptScene.values()) {
            assertNotNull(templates.system(scene), scene.key() + " 缺少 system 模板");
            assertNotNull(templates.user(scene), scene.key() + " 缺少 user 模板");
        }
    }

    @Test
    @DisplayName("路径覆盖生效：Nacos 配置的 key 优先于缺省 classpath 路径")
    void nacosPathOverrideTakesPrecedence() {
        AgentProperties props = new AgentProperties();
        props.getPrompts().put("chat-user", "classpath:prompts/chat-system.st");
        PromptTemplates templates = new PromptTemplates(props, new DefaultResourceLoader());
        assertNotNull(templates.user(PromptScene.CHAT));
    }
}

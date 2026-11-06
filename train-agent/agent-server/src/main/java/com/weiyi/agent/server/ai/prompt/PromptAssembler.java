package com.weiyi.agent.server.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 统一消息装配：任何模型调用的 system 提示词都必须经此处追加代码级安全常量，
 * 分支不得直接向 ChatClient 传裸 system 文本；不可信数据段由装配层净化并按需随机边界包裹。
 */
@Component
public class PromptAssembler {

    /** 随机边界段名白名单：段名只能来自服务端定义，不可由输入内容决定 */
    private static final Set<String> BOUNDARY_LABELS =
            Set.of("input", "history", "memory", "tool", "card", "summary");

    /**
     * 组装最终 system 提示词：模板渲染结果 + 反注入常量；面向用户的回复分支再追加业务安全常量。
     * 安全常量只在此处追加且仅一次，模板内不重复维护安全段。
     */
    public String secureSystem(String renderedSystem, boolean userFacing) {
        return renderedSystem + "\n\n" + PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION
                + (userFacing ? "\n" + PromptSecurityConstants.SERVICE_SAFETY_RULES
                + "\n" + PromptSecurityConstants.USER_FACING_EXPRESSION_RULES : "");
    }

    /**
     * 不可信数据段包裹：净化后套每次随机的边界标签，用于模板未提供静态声明的直接拼接段。
     * uuid 每次装配随机生成，攻击者无法提前伪造闭合标签。
     */
    public String wrap(String label, String content) {
        if (!BOUNDARY_LABELS.contains(label)) {
            throw new IllegalArgumentException("非法数据段名：" + label);
        }
        String tag = PromptSanitizer.nextBoundaryTag(label);
        return "<" + tag + ">\n" + PromptSanitizer.sanitize(content) + "\n</" + tag + ">";
    }
}

package com.weiyi.agent.server.service.chat;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.List;

/**
 * 聊天输入清洗：
 * 文本去首尾空白、长度上限、控制字符——命中直接去除；高危词直接报错
 * 命中只记录类型与长度，不回显原文，避免日志成为泄露面。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSecurityGuard {

    private final AgentProperties agentProperties;

    /**
     * 校验用户文本输入，返回去空白后的内容。
     * 高危词命中拒绝；长度超限拒绝；换行/回车/制表符外的控制字符拒绝。
     */
    public String checkText(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "输入内容不能为空");
        }

        // 先清洗危险不可见字符
        String cleanText = cleanControlAndFormatChar(content);

        // 清洗后重新校验空白
        cleanText = cleanText.strip();
        if (cleanText.isBlank()) {
            log.warn("[security] input cleaned to blank after strip");
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "输入内容不能为空");
        }

        int max = agentProperties.getSecurity().getMaxInputLength();
        if (cleanText.length() > max) {
            log.warn("[security] input too long, cleanLen={}, max={}", cleanText.length(), max);
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "输入内容过长");
        }

        // 敏感词校验
        List<String> riskWords = agentProperties.getSecurity().getHighRiskWords();
        if (!CollectionUtils.isEmpty(riskWords)) {
            for (String riskWord : riskWords) {
                if (cleanText.contains(riskWord)) {
                    log.warn("[security] input rejected: type=high-risk-word, riskWord={}, textLen={}",
                            riskWord, cleanText.length());
                    throw new BusinessException(ErrorCode.AI_CONTENT_REJECTED, "内容包含违规信息");
                }
            }
        }

        return cleanText;
    }

    // 去除隐藏字符，放置隐藏字符有危险语句
    private String cleanControlAndFormatChar(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        int len = raw.length();
        int i = 0;
        while (i < len) {
            int cp = raw.codePointAt(i);
            int step = Character.charCount(cp);

            boolean isIsoControl = Character.isISOControl(cp);
            boolean isFormatChar = Character.getType(cp) == Character.FORMAT;
            boolean isOrphanSurrogate = (cp >= 0xD800 && cp <= 0xDFFF);

            boolean needRemove = false;
            if (isIsoControl) {
                needRemove = cp != '\n' && cp != '\r' && cp != '\t';
            } else if (isFormatChar || isOrphanSurrogate) {
                needRemove = true;
            }

            if (!needRemove) {
                sb.appendCodePoint(cp);
            }
            i += step;
        }
        return sb.toString();
    }

}

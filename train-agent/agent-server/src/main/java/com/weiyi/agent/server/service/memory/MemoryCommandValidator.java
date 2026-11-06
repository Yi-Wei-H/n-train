package com.weiyi.agent.server.service.memory;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 记忆内容校验器：手动新增/修改与自动提炼候选共用同一套规则（POST 不得成为绕过校验的入口）。
 * 敏感信息禁存、操作性指令禁存、注入特征禁存、长度与分类枚举校验；拒绝只记原因码不记原文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCommandValidator {

    /** 允许的分类 */
    private static final Set<String> CATEGORIES = Set.of(
            "TRAVEL_PREFERENCE", "COMMUNICATION_PREFERENCE", "STABLE_CONSTRAINT", "USER_FACT");

    /** 18 位身份证 */
    private static final Pattern ID_CARD =
            Pattern.compile("\\d{6}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]");

    /** 手机号 */
    private static final Pattern MOBILE = Pattern.compile("1[3-9]\\d{9}");

    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    /** 长数字串（银行卡/证件/单号类，≥12 位连续数字） */
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{12,}");

    /** 敏感词类禁存命中（内容性质） */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "密码", "验证码", "密钥", "银行卡", "支付密码", "余额", "身份证", "护照");

    /** 操作性指令禁存：试图改变系统权限或安全标准 */
    private static final List<String> OPERATIONAL_PHRASES = List.of(
            "忽略规则", "忽略安全", "忽略指令", "不用确认", "不用再确认", "自动确认", "直接购买", "跳过确认", "绕过");

    /** 临时性表述：本次有效的偏好不写入长期记忆 */
    private static final List<String> TEMPORAL_MARKERS = List.of("这次", "本次", "这一次", "今天先", "临时");

    private final AgentProperties agentProperties;

    /** 校验记忆正文，违规抛业务异常（错误信息不回显原文） */
    public void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "记忆内容不能为空");
        }
        String text = content.strip();
        if (text.length() > agentProperties.getMemory().getMaxContentLength()) {
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "记忆内容过长");
        }
        if (ID_CARD.matcher(text).find() || MOBILE.matcher(text).find()
                || EMAIL.matcher(text).find() || LONG_DIGITS.matcher(text).find()) {
            reject("sensitive-pattern");
        }
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (text.contains(keyword)) {
                reject("sensitive-keyword");
            }
        }
        for (String phrase : OPERATIONAL_PHRASES) {
            if (text.contains(phrase)) {
                reject("operational-instruction");
            }
        }
    }

    /** 校验分类枚举 */
    public void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "记忆分类不合法");
        }
    }

    /** 临时性表述判定：命中返回 true（不写长期记忆，交由调用方按当次上下文处理） */
    public boolean isTemporaryExpression(String content) {
        if (content == null) {
            return false;
        }
        return TEMPORAL_MARKERS.stream().anyMatch(content::contains);
    }

    private void reject(String reason) {
        log.warn("[security] memory content rejected: reason={}", reason);
        throw new BusinessException(ErrorCode.AI_CONTENT_REJECTED, "该内容不适合保存为长期记忆");
    }
}

package com.weiyi.agent.server.service.memory;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AUserMemory;
import com.weiyi.agent.server.mapper.AUserMemoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 对话内显式记忆指令（"记住/忘掉/你记得我什么"等）：识别后同步执行并返回固定回复文案，
 * 替代当轮模型回复——模型不能自行宣布"已记住"。识别未命中返回 empty，走正常模型链路。
 * 清空属高影响操作，对话内只引导到记忆管理页（需要二次确认，不在聊天流里做两段式状态）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCommandService {

    /** 显式保存：记住X / 以后都X / 帮我记住X */
    private static final Pattern RECORD =
            Pattern.compile("^(?:请|帮我|麻烦)?记住[：:，,]?|以后(?:都|记得|总是)(.*)$");

    /** 忘掉/忘记X */
    private static final Pattern FORGET = Pattern.compile("^(?:请|帮我)?(?:忘掉|忘记|不要再记)(我的)?(.+)$");

    /** 记忆查询 */
    private static final Pattern QUERY = Pattern.compile("你(现在)?记得我(些)?什么|你了解我(的偏好|什么)|我保存(过|了)什么记忆");

    /** 清空全部 */
    private static final Pattern CLEAR = Pattern.compile("清空(所有|全部)?记忆");

    /** 座位类偏好关键词 → 单值键 seat_preference */
    private static final Set<String> SEAT_WORDS = Set.of("靠窗", "过道", "座位", "一等座", "二等座", "商务座", "硬卧", "软卧", "硬座");

    /** 交流类偏好关键词 → 单值键 response_style */
    private static final Set<String> STYLE_WORDS = Set.of("语气", "回答", "回复", "简洁", "详细", "正式", "口语", "中文", "称呼");

    private final MemoryService memoryService;
    private final MemoryCommandValidator validator;
    private final AUserMemoryMapper memoryMapper;

    /**
     * 识别并执行显式记忆指令；返回固定回复文案（已含执行结果），未命中返回 empty。
     */
    public Optional<String> tryExecute(Long userId, String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (CLEAR.matcher(text).find()) {
            return Optional.of("清空记忆影响较大，为避免误操作请在「记忆管理」页面确认后清空。清空后长期记忆不再保留，当前对话不受影响。");
        }
        if (QUERY.matcher(text).find()) {
            return Optional.of(listMemories(userId));
        }
        String recordContent = parseRecord(text);
        if (recordContent != null) {
            return Optional.of(record(userId, recordContent));
        }
        var forget = FORGET.matcher(text);
        if (forget.matches()) {
            return Optional.of(forget(userId, forget.group(2).strip()));
        }
        return Optional.empty();
    }

    private String parseRecord(String text) {
        var m = RECORD.matcher(text);
        if (!m.find()) {
            return null;
        }
        // "以后都X" 捕获组在第二臂；"记住X" 取 find() 之后余段
        if (m.group(1) != null) {
            return m.group(1).strip();
        }
        String rest = text.substring(m.end()).strip();
        return rest.isEmpty() ? null : rest;
    }

    private String record(Long userId, String content) {
        if (!memoryService.enabled(userId)) {
            return "长期记忆总开关当前是关闭的，请先在「记忆管理」页面开启后再让我记住信息。";
        }
        if (validator.isTemporaryExpression(content)) {
            return "这看起来是本次的临时偏好，只对当前对话生效，未保存为长期记忆；想长期记住请说“以后都……”";
        }
        try {
            validator.validateContent(content);
            String key = keyOf(content);
            String category = categoryOf(key);
            memoryService.createExplicit(userId, key, category, content);
            return "已记住：" + content.strip();
        } catch (Exception e) {
            log.warn("[memory] 显式保存被拒绝：userId={}, reason={}", userId, e.getMessage());
            return "这条内容不适合保存为长期记忆（可能包含敏感信息或过长），可以换个更简洁的说法再试。";
        }
    }

    /** 消歧候选列表上限：超出的合并计数提示，避免一次推送无界清单 */
    private static final int FORGET_CANDIDATE_LIMIT = 5;

    private String forget(Long userId, String keyword) {
        if (keyword.isEmpty()) {
            return "想忘掉哪条记忆？可以描述内容关键词，例如“忘掉靠窗偏好”。";
        }
        List<AUserMemory> matches = memoryMapper.selectList(Wrappers.<AUserMemory>lambdaQuery()
                .eq(AUserMemory::getUserId, userId)
                .like(AUserMemory::getContent, escapeLike(keyword))
                .eq(AUserMemory::getStatus, AgentConstants.STATUS_ON));
        if (matches.isEmpty()) {
            return "没有找到与“" + keyword + "”相关的长期记忆。";
        }
        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder("匹配到多条记忆，请说明要忘掉哪一条：\n");
            int shown = Math.min(matches.size(), FORGET_CANDIDATE_LIMIT);
            for (int i = 1; i <= shown; i++) {
                sb.append(i).append(". ").append(matches.get(i - 1).getContent()).append('\n');
            }
            if (matches.size() > FORGET_CANDIDATE_LIMIT) {
                sb.append("（共 ").append(matches.size()).append(" 条，仅列出前 ")
                        .append(FORGET_CANDIDATE_LIMIT).append(" 条，可换更具体的关键词）\n");
            }
            return sb.toString();
        }
        AUserMemory memory = matches.get(0);
        memoryService.delete(userId, memory.getId());
        return "已忘掉：" + memory.getContent();
    }

    /** LIKE 通配符转义（% _ \），防关键词通配全表匹配 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String listMemories(Long userId) {
        String promptText = memoryService.enabledMemoriesForPrompt(userId);
        if ("无".equals(promptText)) {
            return "目前没有保存长期记忆。可以对我说“记住……”，或在「记忆管理」页面添加。";
        }
        return "当前保存的长期记忆：\n" + promptText;
    }

    private String keyOf(String content) {
        if (SEAT_WORDS.stream().anyMatch(content::contains)) {
            return "seat_preference";
        }
        if (STYLE_WORDS.stream().anyMatch(content::contains)) {
            return "response_style";
        }
        if (content.contains("出发") && (content.contains("城市") || content.contains("从"))) {
            return "departure_city";
        }
        // 其余约束类用 MULTI 模式键，按内容指纹去重并存
        return "custom_constraint";
    }

    private String categoryOf(String key) {
        return switch (key) {
            case "seat_preference", "departure_city" -> "TRAVEL_PREFERENCE";
            case "response_style" -> "COMMUNICATION_PREFERENCE";
            default -> "STABLE_CONSTRAINT";
        };
    }
}

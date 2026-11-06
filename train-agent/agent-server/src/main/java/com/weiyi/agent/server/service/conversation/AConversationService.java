package com.weiyi.agent.server.service.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AConversationDTO;
import com.weiyi.agent.api.dto.AConversationQuery;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.mapper.AConversationMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 会话管理：账户级单主会话下创建只由发送链路（AccountConversationService）在指针事务内发生，
 * 管理面只读查询 + 逻辑删除 + 标题重生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AConversationService {

    private static final String DEFAULT_TITLE = AgentConstants.CONVERSATION_TITLE_PLACEHOLDER;

    private final AConversationMapper conversationMapper;

    /** 分页查询：id/status 精确 + title 模糊筛选，按 last_message_at 降序（最新活跃在前）+ id 降序兜底 */
    public PageResult<AConversationDTO> list(AConversationQuery query) {
        String title = query.getTitle() == null ? null : query.getTitle().trim();
        Page<AConversation> page = conversationMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AConversation>lambdaQuery()
                        .eq(query.getId() != null, AConversation::getId, query.getId())
                        .eq(query.getUserId() != null, AConversation::getUserId, query.getUserId())
                        .eq(query.getStatus() != null, AConversation::getStatus, query.getStatus())
                        .like(title != null && !title.isEmpty(), AConversation::getTitle, title)
                        .orderByDesc(AConversation::getLastMessageAt)
                        .orderByDesc(AConversation::getId));
        return PageUtils.convert(page, conversation -> BeanCopyUtils.copy(conversation, AConversationDTO.class));
    }

    /** 逻辑删除 */
    public void deleteById(Long id) {
        getById(id);
        conversationMapper.deleteById(id);
        log.info("删除会话：id={}", id);
    }

    /** 标题 CAS 落库：仅当仍为占位值时更新——MQ 重复消费/并发投递不覆盖已生成标题（方案 C） */
    public boolean updateTitleIfPlaceholder(Long id, String title) {
        return conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .eq(AConversation::getTitle, DEFAULT_TITLE)
                .set(AConversation::getTitle, title)
                .set(AConversation::getTitleStatus, AgentConstants.TITLE_STATUS_SUCCESS)
                .set(AConversation::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    /** 标题直接覆盖：【重新生成标题】接口专用，不受占位值限制 */
    public boolean updateTitle(Long id, String title) {
        return conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .set(AConversation::getTitle, title)
                .set(AConversation::getTitleStatus, AgentConstants.TITLE_STATUS_SUCCESS)
                .set(AConversation::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    /** 首轮标题触发 CAS：NONE→GENERATING 并记录触发时间，返回是否首次触发（仅创建会话的首轮生效） */
    public boolean markTitleGenerating(Long id) {
        return conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .eq(AConversation::getTitleStatus, AgentConstants.TITLE_STATUS_NONE)
                .set(AConversation::getTitleStatus, AgentConstants.TITLE_STATUS_GENERATING)
                .set(AConversation::getTitleTriggeredAt, LocalDateTime.now())
                .set(AConversation::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    /** 标题任务最终失败：仅占位值可置 FAILED（不覆盖已成功/已改写的标题），此后不再自愈补发 */
    public void markTitleFailed(Long id) {
        conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .eq(AConversation::getTitle, DEFAULT_TITLE)
                .set(AConversation::getTitleStatus, AgentConstants.TITLE_STATUS_FAILED)
                .set(AConversation::getUpdatedAt, LocalDateTime.now()));
    }

    /** 首建会话（账户指针链路专用）：占位标题 + 正常状态 + 首次交互时间 */
    public Long createFirstConversation(Long userId, Long chatModelId) {
        AConversation conversation = new AConversation();
        conversation.setUserId(userId);
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setMessageCount(0);
        conversation.setChatModelId(chatModelId);
        conversation.setStatus(AgentConstants.STATUS_ON);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastInteractionAt(LocalDateTime.now());
        conversation.setTitleStatus(AgentConstants.TITLE_STATUS_NONE);
        conversationMapper.insert(conversation);
        log.info("首建会话：id={} userId={} chatModelId={}", conversation.getId(), userId, chatModelId);
        return conversation.getId();
    }

    /** 刷新最近有效交互时间（发送链路事务内调用，标题/系统任务不刷新） */
    public void touchLastInteraction(Long id) {
        conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .set(AConversation::getLastInteractionAt, LocalDateTime.now())
                .set(AConversation::getUpdatedAt, LocalDateTime.now()));
    }

    /** 翻篇归档 CAS：仅正常状态可归档，返回是否生效（并发翻篇/已归档不重复生效） */
    public boolean archiveIfActive(Long id) {
        return conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .eq(AConversation::getStatus, AgentConstants.STATUS_ON)
                .set(AConversation::getStatus, AgentConstants.STATUS_OFF)
                .set(AConversation::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    /** 轮数聚合 +1（消息写入链路事务内调用），返回更新后的轮数（摘要任务触发依据） */
    public int incrementMessageCount(Long id) {
        conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .setSql("message_count = message_count + 1")
                .set(AConversation::getLastMessageAt, LocalDateTime.now())
                .set(AConversation::getUpdatedAt, LocalDateTime.now()));
        return conversationMapper.selectById(id).getMessageCount();
    }

    /** 摘要水位 CAS 写入：仅当新水位大于已覆盖水位（或尚未生成摘要）才生效，防旧任务回退覆盖 */
    public boolean updateSummaryIfNewer(Long id, Long watermarkMessageId, String summary) {
        return conversationMapper.update(null, Wrappers.<AConversation>lambdaUpdate()
                .eq(AConversation::getId, id)
                .and(w -> w.isNull(AConversation::getSummaryMessageId)
                        .or().lt(AConversation::getSummaryMessageId, watermarkMessageId))
                .set(AConversation::getSummary, summary)
                .set(AConversation::getSummaryMessageId, watermarkMessageId)
                .set(AConversation::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    public AConversation getById(Long id) {
        AConversation conversation = conversationMapper.selectById(id);
        BizAssert.notNull(conversation, ErrorCode.PARAM_ERROR, "会话不存在");
        return conversation;
    }
}

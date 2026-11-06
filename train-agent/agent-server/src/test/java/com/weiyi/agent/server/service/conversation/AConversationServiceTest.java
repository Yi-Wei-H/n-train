package com.weiyi.agent.server.service.conversation;
import com.weiyi.agent.server.service.conversation.AConversationService;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AConversationDTO;
import com.weiyi.agent.api.dto.AConversationQuery;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.mapper.AConversationMapper;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话管理测试：AConversationMapper 用 Mockito 替身，不依赖数据库。
 */
class AConversationServiceTest {

    private AConversationMapper conversationMapper;
    private AConversationService conversationService;

    /** 渲染 wrapper SQL（lambda 列名解析）需要 TableInfo 缓存，单测无 MP 启动流程须手动注册 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AConversation.class);
    }

    @BeforeEach
    void setUp() {
        conversationMapper = mock(AConversationMapper.class);
        conversationService = new AConversationService(conversationMapper);
    }

    @Test
    @DisplayName("删除：存在则逻辑删除")
    void testDelete() {
        when(conversationMapper.selectById(9L)).thenReturn(existingRow());

        conversationService.deleteById(9L);

        verify(conversationMapper).deleteById(9L);
    }

    @Test
    @DisplayName("删除：会话不存在拒绝（40000）")
    void testDeleteNotExists() {
        when(conversationMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.deleteById(404L));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("查询：id/status 精确 + title 模糊，按最近消息时间降序")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testListFiltersAndOrder() {
        Page<AConversation> page = new Page<>(1, 10);
        page.setRecords(List.of(existingRow()));
        when(conversationMapper.selectPage(any(), any())).thenReturn(page);

        AConversationQuery query = new AConversationQuery();
        query.setId(9L);
        query.setStatus(AgentConstants.STATUS_ON);
        query.setTitle("查票");
        PageResult<AConversationDTO> result = conversationService.list(query);

        assertEquals(1, result.getList().size());
        assertEquals("查票会话", result.getList().get(0).getTitle());
        assertEquals(3, result.getList().get(0).getMessageCount());

        ArgumentCaptor<Wrapper<AConversation>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(conversationMapper).selectPage(any(), captor.capture());
        AbstractWrapper<AConversation, ?, ?> wrapper = (AbstractWrapper<AConversation, ?, ?>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("id ="), "按会话 id 精确筛选");
        assertTrue(sql.contains("status"), "按状态精确筛选");
        assertTrue(sql.contains("title LIKE"), "标题模糊筛选");
        assertTrue(sql.contains("ORDER BY last_message_at DESC"), "最近消息时间降序（最新活跃在前）");
    }

    private AConversation existingRow() {
        AConversation row = new AConversation();
        row.setId(9L);
        row.setUserId(100L);
        row.setTitle("查票会话");
        row.setChatModelId(5L);
        row.setMessageCount(3);
        row.setStatus(AgentConstants.STATUS_ON);
        return row;
    }
}

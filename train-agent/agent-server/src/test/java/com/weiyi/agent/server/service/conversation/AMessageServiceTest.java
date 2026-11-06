package com.weiyi.agent.server.service.conversation;
import com.weiyi.agent.server.service.conversation.AMessageService;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.api.dto.AMessageQuery;
import com.weiyi.agent.server.req.AMessageReq;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.mapper.AMessageMapper;
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
 * 会话消息读写测试：AMessageMapper 用 Mockito 替身，不依赖数据库。
 */
class AMessageServiceTest {

    private AMessageMapper messageMapper;
    private AMessageService messageService;

    /** 渲染 wrapper SQL（lambda 列名解析）需要 TableInfo 缓存，单测无 MP 启动流程须手动注册 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AMessage.class);
    }

    @BeforeEach
    void setUp() {
        messageMapper = mock(AMessageMapper.class);
        when(messageMapper.insert(any(AMessage.class))).thenReturn(1);
        messageService = new AMessageService(messageMapper);
    }

    @Test
    @DisplayName("新增：一轮对话字段按入参落库")
    void testCreate() {
        AMessageReq req = new AMessageReq();
        req.setConversationId(7L);
        req.setRole("user");
        req.setUserContent("请帮我查询北京到上海的高铁余票");
        req.setReply("已为你查到 G1 次");
        req.setCardId(5L);
        req.setChatModelId(3L);
        req.setUsageLogId(99L);

        messageService.create(req);

        ArgumentCaptor<AMessage> captor = ArgumentCaptor.forClass(AMessage.class);
        verify(messageMapper).insert(captor.capture());
        AMessage saved = captor.getValue();
        assertEquals(7L, saved.getConversationId());
        assertEquals("user", saved.getRole());
        assertEquals("请帮我查询北京到上海的高铁余票", saved.getUserContent());
        assertEquals("已为你查到 G1 次", saved.getReply());
        assertEquals(5L, saved.getCardId());
        assertEquals(3L, saved.getChatModelId());
        assertEquals(99L, saved.getUsageLogId());
    }

    @Test
    @DisplayName("新增：触发方不在白名单拒绝（40000）")
    void testCreateIllegalRole() {
        AMessageReq req = new AMessageReq();
        req.setConversationId(7L);
        req.setRole("bot");
        req.setUserContent("hello");

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.create(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("新增：用户轮缺输入内容与卡片拒绝（40000）")
    void testCreateUserRoundWithoutInput() {
        AMessageReq req = new AMessageReq();
        req.setConversationId(7L);
        req.setRole("user");
        req.setReply("无输入的轮次");

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.create(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("查询：按 id 倒序（最新在前），筛选条件进 WHERE")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testListOrderAndFilters() {
        Page<AMessage> page = new Page<>(1, 20);
        page.setRecords(List.of(row()));
        when(messageMapper.selectPage(any(), any())).thenReturn(page);

        AMessageQuery query = new AMessageQuery();
        query.setConversationId(7L);
        query.setChatModelId(3L);
        PageResult<AMessageDTO> result = messageService.list(query);

        assertEquals(1, result.getList().size());
        assertEquals("assistant", result.getList().get(0).getRole());
        assertEquals("已为你查到 G1 次", result.getList().get(0).getReply());

        ArgumentCaptor<Wrapper<AMessage>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(messageMapper).selectPage(any(), captor.capture());
        AbstractWrapper<AMessage, ?, ?> wrapper = (AbstractWrapper<AMessage, ?, ?>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("conversation_id"), "须按会话筛选");
        assertTrue(sql.contains("chat_model_id"), "须按文字模型筛选");
        assertTrue(sql.contains("ORDER BY id DESC"), "按 id 倒序=最新一轮在前");
    }

    @Test
    @DisplayName("查询：不带筛选默认排除 system 轮，仅排序")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testListWithoutFilters() {
        Page<AMessage> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(messageMapper.selectPage(any(), any())).thenReturn(page);

        AMessageQuery query = new AMessageQuery();
        messageService.list(query);

        ArgumentCaptor<Wrapper<AMessage>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(messageMapper).selectPage(any(), captor.capture());
        AbstractWrapper<AMessage, ?, ?> wrapper = (AbstractWrapper<AMessage, ?, ?>) captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("role <>"), "默认排除 system 轮");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("system"), "排除条件绑定 system");
        assertTrue(wrapper.getSqlSegment().contains("ORDER BY id DESC"));
    }

    @Test
    @DisplayName("上滑翻页：按 beforeId 取更早、排除 system、倒序限量")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testDescBeforeUsesBeforeIdAndLimit() {
        when(messageMapper.selectList(any())).thenReturn(List.of(row()));

        List<AMessage> rows = messageService.descBefore(7L, 100L, 11);

        assertEquals(1, rows.size());
        ArgumentCaptor<Wrapper<AMessage>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(messageMapper).selectList(captor.capture());
        AbstractWrapper<AMessage, ?, ?> wrapper = (AbstractWrapper<AMessage, ?, ?>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("conversation_id"), "须按会话筛选");
        assertTrue(sql.contains("id <"), "须取 beforeId 之前");
        assertTrue(sql.contains("role <>"), "排除 system 轮");
        assertTrue(sql.contains("ORDER BY id DESC"), "倒序取最新优先");
        assertTrue(sql.contains("LIMIT 11"), "多取一条判断 hasMore");
    }

    private AMessage row() {
        AMessage row = new AMessage();
        row.setId(100L);
        row.setConversationId(7L);
        row.setRole("assistant");
        row.setUserContent("北京到上海的高铁余票");
        row.setReply("已为你查到 G1 次");
        row.setChatModelId(3L);
        return row;
    }
}

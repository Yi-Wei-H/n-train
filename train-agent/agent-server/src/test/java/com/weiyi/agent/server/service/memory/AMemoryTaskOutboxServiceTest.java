package com.weiyi.agent.server.service.memory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AMemoryTaskOutbox;
import com.weiyi.agent.server.mapper.AMemoryTaskOutboxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 状态机：Relay 先发消息后回写 SENT，消费端可能先收到消息——
 * PENDING 与 SENT 都必须可消费，否则消息被当重复投递丢弃、任务永远停在 SENT。
 */
class AMemoryTaskOutboxServiceTest {

    private AMemoryTaskOutboxMapper outboxMapper;
    private MemoryTaskOutboxService outboxService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AMemoryTaskOutbox.class);
    }

    @BeforeEach
    void setUp() {
        outboxMapper = mock(AMemoryTaskOutboxMapper.class);
        outboxService = new MemoryTaskOutboxService(outboxMapper);
    }

    @Test
    @DisplayName("消费前置检查：PENDING（先于回写收到消息）与 SENT 都可消费")
    void testPendingAndSentConsumable() {
        when(outboxMapper.selectById(1L)).thenReturn(task(AgentConstants.MEMORY_TASK_PENDING));
        when(outboxMapper.selectById(2L)).thenReturn(task(AgentConstants.MEMORY_TASK_SENT));

        assertTrue(outboxService.isConsumable(1L), "先于状态回写到达的消息不能丢");
        assertTrue(outboxService.isConsumable(2L));
    }

    @Test
    @DisplayName("消费前置检查：终态与缺失任务不可消费")
    void testTerminalNotConsumable() {
        when(outboxMapper.selectById(3L)).thenReturn(task(AgentConstants.MEMORY_TASK_CONSUMED));
        when(outboxMapper.selectById(4L)).thenReturn(task(AgentConstants.MEMORY_TASK_FAILED));

        assertFalse(outboxService.isConsumable(3L));
        assertFalse(outboxService.isConsumable(4L));
        assertFalse(outboxService.isConsumable(99L));
    }

    @Test
    @DisplayName("消费完成：CAS 覆盖 PENDING/SENT，不误伤终态")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testMarkConsumedCasOnOpenStates() {
        outboxService.markConsumed(7L);

        ArgumentCaptor<Wrapper<AMemoryTaskOutbox>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(outboxMapper).update(any(), captor.capture());
        AbstractWrapper<AMemoryTaskOutbox, ?, ?> wrapper = (AbstractWrapper<AMemoryTaskOutbox, ?, ?>) captor.getValue();
        String sql = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sql.contains("STATUS IN"), "仅开放状态可置终态：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AgentConstants.MEMORY_TASK_PENDING));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AgentConstants.MEMORY_TASK_SENT));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AgentConstants.MEMORY_TASK_CONSUMED));
    }

    private AMemoryTaskOutbox task(int status) {
        AMemoryTaskOutbox task = new AMemoryTaskOutbox();
        task.setId(1L);
        task.setStatus(status);
        task.setAttempts(0);
        return task;
    }
}

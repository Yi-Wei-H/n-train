package com.weiyi.agent.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AQuickCardDTO;
import com.weiyi.agent.api.dto.AQuickCardQuery;
import com.weiyi.agent.api.dto.AQuickCardReq;
import com.weiyi.agent.server.dto.AQuickCardShowDTO;
import com.weiyi.agent.server.entity.AQuickCard;
import com.weiyi.agent.server.mapper.AQuickCardMapper;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 便捷卡片管理测试：AQuickCardMapper 用 Mockito 替身，不依赖数据库。
 */
class AQuickCardServiceTest {

    private AQuickCardMapper quickCardMapper;
    private AQuickCardService quickCardService;

    /** 渲染 wrapper SQL（lambda 列名解析）需要 TableInfo 缓存，单测无 MP 启动流程须手动注册 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AQuickCard.class);
    }

    @BeforeEach
    void setUp() {
        quickCardMapper = mock(AQuickCardMapper.class);
        when(quickCardMapper.insert(any(AQuickCard.class))).thenReturn(1);
        quickCardService = new AQuickCardService(quickCardMapper);
    }

    @Test
    @DisplayName("新增：缺省上架、排序 0、窗口/图标为空")
    void testCreateDefaults() {
        AQuickCardReq req = new AQuickCardReq();
        req.setCardName("查询余票");
        req.setPromptText("请帮我查询北京到上海未来三天的高铁余票");

        quickCardService.createOrUpdate(req);

        ArgumentCaptor<AQuickCard> captor = ArgumentCaptor.forClass(AQuickCard.class);
        verify(quickCardMapper).insert(captor.capture());
        AQuickCard saved = captor.getValue();
        assertEquals("查询余票", saved.getCardName());
        assertEquals(AgentConstants.STATUS_ON, saved.getStatus());
        assertEquals(0, saved.getSortNo());
        assertNull(saved.getIcon());
        assertNull(saved.getStartAt());
        assertNull(saved.getEndAt());
    }

    @Test
    @DisplayName("新增：显式下架、排序、图标与活动窗口按入参落库")
    void testCreateWithValues() {
        AQuickCardReq req = new AQuickCardReq();
        req.setCardName("春运活动");
        req.setPromptText("帮我查春运首日的票");
        req.setIcon("spring");
        req.setSortNo(5);
        req.setStatus(AgentConstants.STATUS_OFF);
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(7);
        req.setStartAt(start);
        req.setEndAt(end);

        quickCardService.createOrUpdate(req);

        ArgumentCaptor<AQuickCard> captor = ArgumentCaptor.forClass(AQuickCard.class);
        verify(quickCardMapper).insert(captor.capture());
        AQuickCard saved = captor.getValue();
        assertEquals(AgentConstants.STATUS_OFF, saved.getStatus());
        assertEquals(5, saved.getSortNo());
        assertEquals("spring", saved.getIcon());
        assertEquals(start, saved.getStartAt());
        assertEquals(end, saved.getEndAt());
    }

    @Test
    @DisplayName("校验：非法状态值拒绝（40000）")
    void testCreateIllegalStatus() {
        AQuickCardReq req = new AQuickCardReq();
        req.setCardName("查询余票");
        req.setPromptText("查票");
        req.setStatus(3);

        BusinessException ex = assertThrows(BusinessException.class, () -> quickCardService.createOrUpdate(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("校验：活动开始晚于结束拒绝（40000）")
    void testCreateWindowReversed() {
        AQuickCardReq req = new AQuickCardReq();
        req.setCardName("春运活动");
        req.setPromptText("查票");
        req.setStartAt(LocalDateTime.now().plusDays(7));
        req.setEndAt(LocalDateTime.now().minusDays(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> quickCardService.createOrUpdate(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("更新：全量快照——icon/活动窗口传空即清空，status 传空保持原值，sortNo 传空归 0")
    void testUpdateSnapshot() {
        AQuickCard existing = existingRow();
        when(quickCardMapper.selectById(9L)).thenReturn(existing);

        AQuickCardReq req = new AQuickCardReq();
        req.setId(9L);
        req.setCardName("查询我的车票");
        req.setPromptText("请帮我查询我最近购买的车票");
        // icon/sortNo/startAt/endAt/status 全部传空
        LocalDateTime end = LocalDateTime.now().plusDays(3);
        req.setEndAt(end);

        quickCardService.createOrUpdate(req);

        ArgumentCaptor<AQuickCard> captor = ArgumentCaptor.forClass(AQuickCard.class);
        verify(quickCardMapper).updateById(captor.capture());
        AQuickCard saved = captor.getValue();
        assertEquals(9L, saved.getId());
        assertEquals("查询我的车票", saved.getCardName());
        assertNull(saved.getIcon(), "icon 传空即清空");
        assertNull(saved.getStartAt(), "start_at 传空即清空");
        assertEquals(end, saved.getEndAt());
        assertEquals(0, saved.getSortNo(), "sortNo 传空归 0");
        assertEquals(AgentConstants.STATUS_ON, saved.getStatus(), "status 传空保持原值");
    }

    @Test
    @DisplayName("更新：卡片不存在拒绝（40000）")
    void testUpdateNotExists() {
        when(quickCardMapper.selectById(404L)).thenReturn(null);

        AQuickCardReq req = new AQuickCardReq();
        req.setId(404L);
        req.setCardName("查询余票");
        req.setPromptText("查票");

        BusinessException ex = assertThrows(BusinessException.class, () -> quickCardService.createOrUpdate(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("删除：存在则逻辑删除")
    void testDelete() {
        AQuickCard existing = existingRow();
        when(quickCardMapper.selectById(9L)).thenReturn(existing);

        quickCardService.deleteById(9L);

        verify(quickCardMapper).deleteById(9L);
    }

    @Test
    @DisplayName("删除：卡片不存在拒绝（40000）")
    void testDeleteNotExists() {
        when(quickCardMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> quickCardService.deleteById(404L));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("用户端有效卡片：只回展示四字段，过滤含 上架+两个窗口边界，按 sort_no 排序")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testListActive() {
        when(quickCardMapper.selectList(any())).thenReturn(List.of(existingRow()));

        List<AQuickCardShowDTO> result = quickCardService.listActive();

        assertEquals(1, result.size());
        AQuickCardShowDTO dto = result.get(0);
        assertEquals(9L, dto.getId());
        assertEquals("查询我的车票", dto.getCardName());
        assertEquals("ticket", dto.getIcon());
        assertEquals(2, dto.getSortNo());

        ArgumentCaptor<Wrapper<AQuickCard>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(quickCardMapper).selectList(captor.capture());
        AbstractWrapper<AQuickCard, ?, ?> wrapper = (AbstractWrapper<AQuickCard, ?, ?>) captor.getValue();
        // MP 参数段是惰性 lambda，getSqlSegment() 渲染时才填充 paramNameValuePairs，须先渲染再断言参数
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("status"), "过滤条件须含 status");
        assertTrue(sql.contains("start_at IS NULL"), "start_at 空=常驻（OR IS NULL 分支）");
        assertTrue(sql.contains("end_at IS NULL"), "end_at 空=常驻（OR IS NULL 分支）");
        assertTrue(sql.contains("sort_no"), "按 sort_no 排序");
        List<Object> params = List.copyOf(wrapper.getParamNameValuePairs().values());
        assertTrue(params.contains(AgentConstants.STATUS_ON), "参数须含 status=上架");
        assertEquals(2, params.stream().filter(v -> v instanceof LocalDateTime).count(), "须有 start/end 两个窗口边界参数");
    }

    @Test
    @DisplayName("管理端分页：结果映射为完整 DTO")
    void testAdminList() {
        Page<AQuickCard> page = new Page<>(1, 10);
        page.setRecords(List.of(existingRow()));
        when(quickCardMapper.selectPage(any(), any())).thenReturn(page);

        AQuickCardQuery query = new AQuickCardQuery();
        PageResult<AQuickCardDTO> result = quickCardService.list(query);

        assertEquals(1, result.getList().size());
        AQuickCardDTO dto = result.getList().get(0);
        assertEquals("查询我的车票", dto.getCardName());
        assertEquals("请帮我查询我最近购买的车票", dto.getPromptText());
        assertEquals(AgentConstants.STATUS_ON, dto.getStatus());
    }

    private AQuickCard existingRow() {
        AQuickCard row = new AQuickCard();
        row.setId(9L);
        row.setCardName("查询我的车票");
        row.setPromptText("请帮我查询我最近购买的车票");
        row.setIcon("ticket");
        row.setSortNo(2);
        row.setStatus(AgentConstants.STATUS_ON);
        row.setStartAt(LocalDateTime.now().minusDays(1));
        row.setEndAt(LocalDateTime.now().plusDays(1));
        return row;
    }
}

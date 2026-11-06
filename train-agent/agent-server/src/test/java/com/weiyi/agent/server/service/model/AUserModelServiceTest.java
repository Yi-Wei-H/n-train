package com.weiyi.agent.server.service.model;
import com.weiyi.agent.server.service.model.AUserModelService;

import com.weiyi.agent.server.entity.AUserModel;
import com.weiyi.agent.server.mapper.AUserModelMapper;
import com.weiyi.agent.api.dto.AUserModelReq;
import com.weiyi.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户授权行管理测试：AUserModelMapper 用 Mockito 替身，不依赖数据库。
 */
class AUserModelServiceTest {

    private AUserModelMapper userModelMapper;
    private AUserModelService userModelService;

    @BeforeEach
    void setUp() {
        userModelMapper = mock(AUserModelMapper.class);
        when(userModelMapper.selectCount(any())).thenReturn(0L);
        when(userModelMapper.insert(any(AUserModel.class))).thenReturn(1);
        userModelService = new AUserModelService(userModelMapper);
    }

    @Test
    @DisplayName("首次创建：默认行 model_id 空、status 可用、配额不限")
    void testCreateDefaults() {
        userModelService.createByUserId(100L);

        ArgumentCaptor<AUserModel> captor = ArgumentCaptor.forClass(AUserModel.class);
        verify(userModelMapper).insert(captor.capture());
        AUserModel saved = captor.getValue();
        assertEquals(100L, saved.getUserId());
        assertNull(saved.getModelId(), "model_id 空=跟随当前启用模型");
        assertEquals(1, saved.getStatus());
        assertEquals(0, saved.getDailyQuota());
    }

    @Test
    @DisplayName("幂等：授权行已存在时跳过创建")
    void testIdempotentSkip() {
        when(userModelMapper.selectCount(any())).thenReturn(1L);

        userModelService.createByUserId(100L);

        verify(userModelMapper, never()).insert(any(AUserModel.class));
    }

    @Test
    @DisplayName("入参校验：userId 为空拒绝（40000）")
    void testNullUserIdRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userModelService.createByUserId(null));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("更新：status/dailyQuota 非空覆盖，model_id 不动")
    void testUpdateOverrides() {
        AUserModel existing = existingRow();
        when(userModelMapper.selectList(any())).thenReturn(List.of(existing));

        AUserModelReq req = new AUserModelReq();
        req.setUserId(100L);
        req.setStatus(2);
        req.setDailyQuota(50);
        userModelService.updateByUserId(req);

        ArgumentCaptor<AUserModel> captor = ArgumentCaptor.forClass(AUserModel.class);
        verify(userModelMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
        assertEquals(50, captor.getValue().getDailyQuota());
        assertNull(captor.getValue().getModelId(), "model_id 不经更新接口修改");
    }

    @Test
    @DisplayName("更新：授权行不存在拒绝（40000）")
    void testUpdateNotExists() {
        when(userModelMapper.selectList(any())).thenReturn(List.of());

        AUserModelReq req = new AUserModelReq();
        req.setUserId(100L);
        BusinessException ex = assertThrows(BusinessException.class, () -> userModelService.updateByUserId(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("更新：非法状态值拒绝（40000）")
    void testUpdateIllegalStatus() {
        AUserModelReq req = new AUserModelReq();
        req.setUserId(100L);
        req.setStatus(3);
        BusinessException ex = assertThrows(BusinessException.class, () -> userModelService.updateByUserId(req));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("删除：存在则逻辑删除")
    void testDelete() {
        AUserModel existing = existingRow();
        when(userModelMapper.selectList(any())).thenReturn(List.of(existing));

        userModelService.deleteByUserId(100L);

        verify(userModelMapper).deleteById(existing.getId());
    }

    @Test
    @DisplayName("删除：授权行不存在拒绝（40000）")
    void testDeleteNotExists() {
        when(userModelMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> userModelService.deleteByUserId(100L));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("读路径补建：缺行时按默认值建行并返回（注册联动失败也能自愈）")
    void testEnsureCreatesWhenMissing() {
        AUserModel created = existingRow();
        // 首次查询为空触发补建，补建后再查返回新行
        when(userModelMapper.selectList(any())).thenReturn(List.of(), List.of(created));

        AUserModel row = userModelService.ensureByUserId(100L);

        assertEquals(100L, row.getUserId());
        assertEquals(1, row.getStatus(), "补建为可用");
        assertEquals(0, row.getDailyQuota(), "补建为不限额度");
        verify(userModelMapper).insert(any(AUserModel.class));
    }

    @Test
    @DisplayName("读路径补建：已有行时不重复插入")
    void testEnsureNoDuplicate() {
        when(userModelMapper.selectList(any())).thenReturn(List.of(existingRow()));

        AUserModel row = userModelService.ensureByUserId(100L);

        assertEquals(100L, row.getUserId());
        verify(userModelMapper, never()).insert(any(AUserModel.class));
    }

    private AUserModel existingRow() {
        AUserModel row = new AUserModel();
        row.setId(9L);
        row.setUserId(100L);
        row.setStatus(1);
        row.setDailyQuota(0);
        return row;
    }
}

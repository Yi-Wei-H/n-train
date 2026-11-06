package com.weiyi.agent.server.service.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.api.dto.AModelPresetDTO;
import com.weiyi.agent.api.dto.AModelPresetQuery;
import com.weiyi.agent.api.dto.AModelPresetReq;
import com.weiyi.agent.server.entity.AModelPreset;
import com.weiyi.agent.server.mapper.AModelPresetMapper;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class AModelPresetService {

    private final AModelPresetMapper aModelPresetMapper;

    /**
     *  上下架切换（agent 域约定：1上架 2下架）
     * @param id 预设ID
     */
    public void updateStatusById(Long id) {
        AModelPreset aModelPreset = aModelPresetMapper.selectById(id);
        BizAssert.notNull(aModelPreset, ErrorCode.MODEL_PRESET_NOT_EXISTS);
        if (aModelPreset.getStatus() == AgentConstants.STATUS_ON) {
            aModelPreset.setStatus(AgentConstants.STATUS_OFF);
            log.info("已将预设ID：{}，更新为下架",aModelPreset.getId());
        } else {
            aModelPreset.setStatus(AgentConstants.STATUS_ON);
            log.info("已将预设ID：{}，更新为上架",aModelPreset.getId());
        }
        aModelPresetMapper.updateById(aModelPreset);
    }

    public PageResult<AModelPresetDTO> myModelPresets (AModelPresetQuery query) {
        log.info("查询模型预设，传入参数：{}", query);
        Page<AModelPreset> modelPresetPage = aModelPresetMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AModelPreset>lambdaQuery()
                        .eq(query.getModelType() != null, AModelPreset::getModelType, query.getModelType())
                        .orderByAsc(AModelPreset::getSortNo)
        );
        return PageUtils.convert(modelPresetPage, preset -> BeanCopyUtils.copy(preset, AModelPresetDTO.class));
    }

    /**
     *  新增或更新预设：id 为空走新增（全量拷贝），否则按 id 局部更新（非空字段覆盖）
     */
    public void createOrUpdatePreset(@Valid AModelPresetReq aModelPresetReq) {
        if (aModelPresetReq.getId() == null) {
            AModelPreset preset = new AModelPreset();
            BeanCopyUtils.copy(aModelPresetReq, preset);
            preset.setStatus(AgentConstants.STATUS_ON);
            aModelPresetMapper.insert(preset);
            log.info("新增模型预设：id={} vendor={} apiModelId={}", preset.getId(), preset.getVendor(), preset.getApiModelId());
        } else {
            AModelPreset preset = aModelPresetMapper.selectById(aModelPresetReq.getId());
            BizAssert.notNull(preset, ErrorCode.MODEL_PRESET_NOT_EXISTS);
            BeanCopyUtils.copyNonNull(aModelPresetReq, preset);
            aModelPresetMapper.updateById(preset);
            log.info("更新模型预设：id={} vendor={}", preset.getId(), preset.getVendor());
        }
    }

    public void deleteById(Long id) {
        log.info("删除模型预设ID:{}", id);
        AModelPreset aModelPreset = aModelPresetMapper.selectById(id);
        BizAssert.notNull(aModelPreset, ErrorCode.MODEL_PRESET_NOT_EXISTS);
        aModelPresetMapper.deleteById(id);
    }
}

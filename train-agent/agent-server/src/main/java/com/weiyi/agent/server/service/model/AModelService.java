package com.weiyi.agent.server.service.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.ai.provider.CorrectBaseURL;
import com.weiyi.agent.server.ai.provider.ModelConfigChangedEvent;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AModelDTO;
import com.weiyi.agent.api.dto.AModelQuery;
import com.weiyi.agent.api.dto.AModelReq;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.mapper.AModelMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AModelService {

    private final AModelMapper aModelMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 新增或更新模型配置：id 为空走新增（全量拷贝、固定下架），否则查出实体后非空字段覆盖。
     * 保存导致 下架→上架 切换时，自动关闭同类型原启用模型（同类型单活跃）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdateModel(AModelReq req) {
        normalizeAndValidate(req);
        if (req.getId() == null) {
            BizAssert.notBlank(req.getApiKey(), ErrorCode.PARAM_ERROR, "API KEY 不能为空");
            AModel model = new AModel();
            BeanCopyUtils.copy(req, model);
            model.setStatus(AgentConstants.STATUS_OFF);
            aModelMapper.insert(model);
            publishChanged(model.getId());
            log.info("新增模型配置（默认下架）：id={} provider={} apiModelId={}", model.getId(), model.getProvider(), model.getApiModelId());
        } else {
            AModel model = getModelById(req.getId());
            BizAssert.isTrue(Objects.equals(req.getModelType(), model.getModelType()),
                    ErrorCode.PARAM_ERROR, "模型类型不允许修改（文本/向量不可互转）");
            boolean turningOn = Objects.equals(req.getStatus(), AgentConstants.STATUS_ON)
                    && AgentConstants.STATUS_OFF == model.getStatus();
            BeanCopyUtils.copyNonNull(req, model);
            if (turningOn) {
                closeEnabledModelOf(model.getModelType(), model.getId());
            }
            aModelMapper.updateById(model);
            publishChanged(model.getId());
            log.info("更新模型配置：id={} provider={} turningOn={}", model.getId(), model.getProvider(), turningOn);
        }
    }

    /**
     * 保存校验：base_url 归一化（trim + 去尾斜杠）且必须 http(s):// 开头；
     * 认证两维耦合（Bearer 强制清空 auth_key_name，其余方式必填）；
     * Gemini 格式暂无客户端实现，拒绝保存；向量模型仅允许 OpenAI 兼容格式。
     */
    private void normalizeAndValidate(AModelReq req) {
        String baseUrl = CorrectBaseURL.stripTrailingSlashes(req.getBaseUrl());
        CorrectBaseURL.verifyStartHttp(baseUrl);
        req.setBaseUrl(baseUrl);

        if (Objects.equals(req.getAuthType(), AgentConstants.AUTH_BEARER)) {
            req.setAuthKeyName(null);
        } else {
            BizAssert.notBlank(req.getAuthKeyName(), ErrorCode.PARAM_ERROR, "该认证方式必须填写认证字段名 authKeyName");
        }

        BizAssert.isTrue(!Objects.equals(req.getApiFormat(), AgentConstants.API_FORMAT_GEMINI),
                ErrorCode.PARAM_ERROR, "Gemini 格式暂未支持，无法保存");
        // openai-java SDK 强制标准 credential（apiKey()），Key 头/查询参数认证无法构建 OpenAI 系客户端（构建即抛 IllegalStateException）
        BizAssert.isTrue(!(Objects.equals(req.getApiFormat(), AgentConstants.API_FORMAT_OPENAI_CHAT)
                && !Objects.equals(req.getAuthType(), AgentConstants.AUTH_BEARER)),
                ErrorCode.PARAM_ERROR, "OpenAI 兼容格式仅支持 Bearer 认证");
        if (Objects.equals(req.getModelType(), AgentConstants.MODEL_TYPE_EMBEDDING)) {
            BizAssert.isTrue(Objects.equals(req.getApiFormat(), AgentConstants.API_FORMAT_OPENAI_CHAT),
                    ErrorCode.PARAM_ERROR, "向量模型仅支持 OpenAI 兼容格式");
        }
    }

    public PageResult<AModelDTO> list(AModelQuery query) {
        log.info("查询模型配置，modelType={}", query.getModelType());
        Page<AModel> aModelPage = aModelMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AModel>lambdaQuery()
                        .eq(query.getModelType() != null, AModel::getModelType, query.getModelType())
                        .orderByAsc(AModel::getCreatedAt));
        return PageUtils.convert(aModelPage, model -> {
            AModelDTO dto = BeanCopyUtils.copy(model, AModelDTO.class);
            dto.setApiKeyMasked(maskKey(model.getApiKey()));
            return dto;
        });
    }

    /** 出参只给明文尾 4 位掩码：不回传完整 key，配置人凭尾 4 位核对 */
    private static String maskKey(String key) {
        return key == null || key.isBlank() ? null : "••••" + key.substring(Math.max(0, key.length() - 4));
    }

    public void deleteById(Long id) {
        log.info("删除模型配置ID:{}", id);
        getModelById(id);
        aModelMapper.deleteById(id);
        publishChanged(id);
    }

    /**
     * 启停切换（同类型单活跃）：开启时自动关闭同类型原启用模型。
     * 两条 UPDATE 必须同事务——中途失败不能出现"该类型一个启用的都没有"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatusById(Long id) {
        AModel model = getModelById(id);
        if (AgentConstants.STATUS_ON == model.getStatus()) {
            model.setStatus(AgentConstants.STATUS_OFF);
            log.info("关闭模型ID:{}", model.getId());
        } else {
            model.setStatus(AgentConstants.STATUS_ON);
            closeEnabledModelOf(model.getModelType(), model.getId());
            log.info("开启模型ID:{}", model.getId());
        }
        aModelMapper.updateById(model);
        publishChanged(id);
    }

    /** 关闭同类型当前启用的其他模型（排除自己）；没有可关闭的就不动 */
    private void closeEnabledModelOf(Integer modelType, Long selfId) {
        List<AModel> enabled = aModelMapper.selectList(Wrappers.<AModel>lambdaQuery()
                .eq(AModel::getStatus, AgentConstants.STATUS_ON)
                .eq(AModel::getModelType, modelType)
                .ne(AModel::getId, selfId));
        for (AModel m : enabled) {
            m.setStatus(AgentConstants.STATUS_OFF);
            aModelMapper.updateById(m);
            publishChanged(m.getId());
            log.info("自动关闭同类型原启用模型ID：{}", m.getId());
        }
    }

    /** 通知 LlmProviderRegistry 失效该模型的配置/客户端缓存（改配置即时生效，无需重启） */
    private void publishChanged(Long modelId) {
        eventPublisher.publishEvent(new ModelConfigChangedEvent(modelId));
    }

    public AModel getModelById(Long id) {
        AModel model = aModelMapper.selectById(id);
        BizAssert.notNull(model, ErrorCode.MODEL_NOT_EXISTS);
        return model;
    }

    /**
     * 获取当前可用模型
     */
    public AModel getCurrentModel() {
        AModel aModel = aModelMapper.selectOne(Wrappers.<AModel>lambdaQuery()
                .eq(AModel::getStatus, AgentConstants.STATUS_ON)
                .eq(AModel::getModelType, AgentConstants.MODEL_TYPE_CHAT));
        BizAssert.notNull(aModel, ErrorCode.MODEL_NOT_EXISTS, "当前没有可使用的模型");
        return aModel;
    }
}

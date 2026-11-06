package com.weiyi.agent.server.service.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AUserModelDTO;
import com.weiyi.agent.api.dto.AUserModelQuery;
import com.weiyi.agent.api.dto.AUserModelReq;
import com.weiyi.agent.server.entity.AUserModel;
import com.weiyi.agent.server.mapper.AUserModelMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AUserModelService {

    private final AUserModelMapper userModelMapper;

    /**
     * 用户创建时生成默认授权行（幂等）：model_id 空=跟随当前启用模型、status=可用、daily_quota=0 不限。
     * 幂等靠应用层先查后插——uk_user_model(user_id, model_id) 在 PG 下对 model_id=NULL 不去重（NULL 互不相等）。
     */
    public void createByUserId(Long userId) {
        BizAssert.notNull(userId, ErrorCode.PARAM_ERROR, "用户 id 不能为空");
        Long exists = userModelMapper.selectCount(Wrappers.<AUserModel>lambdaQuery()
                .eq(AUserModel::getUserId, userId));
        if (exists != null && exists > 0) {
            log.info("用户授权行已存在，跳过创建：userId={}", userId);
            return;
        }
        AUserModel userModel = new AUserModel();
        userModel.setUserId(userId);
        userModel.setStatus(AgentConstants.STATUS_ON);
        userModel.setDailyQuota(0);
        userModelMapper.insert(userModel);
        log.info("用户默认授权行已创建：userId={}", userId);
    }

    /** 分页查询用户授权（userId / status 可选筛选，按创建时间升序） */
    public PageResult<AUserModelDTO> list(AUserModelQuery query) {
        Page<AUserModel> page = userModelMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AUserModel>lambdaQuery()
                        .eq(query.getUserId() != null, AUserModel::getUserId, query.getUserId())
                        .eq(query.getStatus() != null, AUserModel::getStatus, query.getStatus())
                        .orderByAsc(AUserModel::getCreatedAt));
        return PageUtils.convert(page, row -> BeanCopyUtils.copy(row, AUserModelDTO.class));
    }

    /**
     * 取授权行，缺失时按默认值（可用、不限额度）补建后返回：
     * 注册联动可能因 agent 不可用而未建成，会员侧读路径在此自愈，保证有账号即有配置。
     */
    public AUserModel ensureByUserId(Long userId) {
        BizAssert.notNull(userId, ErrorCode.PARAM_ERROR, "用户 id 不能为空");
        List<AUserModel> rows = rowsOf(userId);
        if (rows.isEmpty()) {
            createByUserId(userId);
            rows = rowsOf(userId);
        }
        warnIfMultiple(rows, userId);
        return rows.getFirst();
    }

    /** 按用户更新授权：status / dailyQuota 非空覆盖，model_id 不经此接口修改（跟随启用模型） */
    public void updateByUserId(AUserModelReq req) {
        if (req.getStatus() != null) {
            BizAssert.isTrue(AgentConstants.STATUS_ON == req.getStatus() || AgentConstants.STATUS_OFF == req.getStatus(),
                    ErrorCode.PARAM_ERROR, "状态只允许 1可用 2禁用");
        }
        if (req.getDailyQuota() != null) {
            BizAssert.isTrue(req.getDailyQuota() >= 0, ErrorCode.PARAM_ERROR, "每日额度不能为负");
        }
        AUserModel userModel = getByUserId(req.getUserId());
        if (req.getStatus() != null) {
            userModel.setStatus(req.getStatus());
        }
        if (req.getDailyQuota() != null) {
            userModel.setDailyQuota(req.getDailyQuota());
        }
        userModelMapper.updateById(userModel);
        log.info("用户授权已更新：userId={}, status={}, dailyQuota={}", userModel.getUserId(), userModel.getStatus(), userModel.getDailyQuota());
    }

    /**
     * 按用户逻辑删除授权行。原则上不开放——授权行由系统在用户创建时生成、随用户生命周期管理，
     * 此接口仅预留纠错/数据治理入口（删除模型时级联逻辑删或提示先解除，方案待定）。
     */
    public void deleteByUserId(Long userId) {
        AUserModel userModel = getByUserId(userId);
        userModelMapper.deleteById(userModel.getId());
        log.info("用户授权行已逻辑删除：userId={}", userId);
    }

    /** 取用户授权行（管理面强校验用：行不存在即报错，不做补建）；多条属数据异常（记警日志不阻断） */
    public AUserModel getByUserId(Long userId) {
        List<AUserModel> rows = rowsOf(userId);
        warnIfMultiple(rows, userId);
        BizAssert.isTrue(!rows.isEmpty(), ErrorCode.PARAM_ERROR, "该用户授权行不存在，请先创建");
        return rows.getFirst();
    }

    private List<AUserModel> rowsOf(Long userId) {
        return userModelMapper.selectList(Wrappers.<AUserModel>lambdaQuery()
                .eq(AUserModel::getUserId, userId));
    }

    private void warnIfMultiple(List<AUserModel> rows, Long userId) {
        if (rows.size() > 1) {
            log.warn("用户存在多条授权行（数据异常，建议清理）：userId={}, count={}", userId, rows.size());
        }
    }
}

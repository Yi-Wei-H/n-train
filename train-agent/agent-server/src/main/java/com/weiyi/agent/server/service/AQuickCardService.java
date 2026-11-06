package com.weiyi.agent.server.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AQuickCardDTO;
import com.weiyi.agent.api.dto.AQuickCardQuery;
import com.weiyi.agent.api.dto.AQuickCardReq;
import com.weiyi.agent.server.dto.AQuickCardShowDTO;
import com.weiyi.agent.server.entity.AQuickCard;
import com.weiyi.agent.server.mapper.AQuickCardMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AQuickCardService {

    private final AQuickCardMapper quickCardMapper;

    /**
     * 新增或编辑卡片：id 空=新增（status 缺省上架随 DDL 默认、sort_no 缺省 0）；
     * 否则全量快照更新——表单整体保存，icon/start_at/end_at 传空即清空（实体列 updateStrategy=ALWAYS），
     * status 传空保持原值。
     */
    public void createOrUpdate(AQuickCardReq req) {
        validate(req);
        if (req.getId() == null) {
            AQuickCard card = new AQuickCard();
            BeanCopyUtils.copy(req, card);
            card.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
            card.setStatus(req.getStatus() == null ? AgentConstants.STATUS_ON : req.getStatus());
            quickCardMapper.insert(card);
            log.info("新增便捷卡片（缺省上架）：id={} cardName={}", card.getId(), card.getCardName());
        } else {
            AQuickCard card = getById(req.getId());
            BizAssert.notNull(card, ErrorCode.QUICK_CARD_NOT_EXISTS);
            BeanCopyUtils.copyNonNull(req, card);
            // 快照字段显式覆盖（copyNonNull 无法置空）：icon/活动窗口传空即清空，sortNo 传空归 0
            card.setIcon(req.getIcon());
            card.setStartAt(req.getStartAt());
            card.setEndAt(req.getEndAt());
            card.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
            quickCardMapper.updateById(card);
            log.info("更新便捷卡片：id={} status={}", card.getId(), card.getStatus());
        }
    }

    private void validate(AQuickCardReq req) {
        if (req.getStatus() != null) {
            BizAssert.isTrue(AgentConstants.STATUS_ON == req.getStatus() || AgentConstants.STATUS_OFF == req.getStatus(),
                    ErrorCode.PARAM_ERROR, "状态只允许 1上架 2下架");
        }
        if (req.getStartAt() != null && req.getEndAt() != null) {
            BizAssert.isTrue(!req.getStartAt().isAfter(req.getEndAt()),
                    ErrorCode.PARAM_ERROR, "活动开始时间不能晚于结束时间");
        }
    }

    /** 管理端分页查询：card_name 模糊 / status 筛选，按 sort_no 升序 */
    public PageResult<AQuickCardDTO> list(AQuickCardQuery query) {
        String name = query.getCardName() == null ? null : query.getCardName().trim();
        Page<AQuickCard> page = quickCardMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AQuickCard>lambdaQuery()
                        .like(name != null && !name.isEmpty(), AQuickCard::getCardName, name)
                        .eq(query.getStatus() != null, AQuickCard::getStatus, query.getStatus())
                        .orderByAsc(AQuickCard::getSortNo)
                        .orderByAsc(AQuickCard::getId));
        return PageUtils.convert(page, card -> BeanCopyUtils.copy(card, AQuickCardDTO.class));
    }

    /**
     * 用户端有效卡片：status=上架 且 活动窗口内（start_at/end_at 空=常驻，PG 下 NULL 比较不成立须显式 OR IS NULL），
     * 按 sort_no 升序；只回展示四字段，不回 prompt_text
     */
    public List<AQuickCardShowDTO> listActive() {
        LocalDateTime now = LocalDateTime.now();
        List<AQuickCard> cards = quickCardMapper.selectList(Wrappers.<AQuickCard>lambdaQuery()
                .eq(AQuickCard::getStatus, AgentConstants.STATUS_ON)
                .and(w -> w.isNull(AQuickCard::getStartAt).or().le(AQuickCard::getStartAt, now))
                .and(w -> w.isNull(AQuickCard::getEndAt).or().ge(AQuickCard::getEndAt, now))
                .orderByAsc(AQuickCard::getSortNo)
                .orderByAsc(AQuickCard::getId));
        return BeanCopyUtils.copyList(cards, AQuickCardShowDTO.class);
    }

    /** 逻辑删除 */
    public void deleteById(Long id) {
        getById(id);
        quickCardMapper.deleteById(id);
        log.info("删除便捷卡片：id={}", id);
    }

    public AQuickCard getById(Long id) {
        AQuickCard card = quickCardMapper.selectById(id);
        BizAssert.notNull(card, ErrorCode.PARAM_ERROR, "卡片不存在");
        return card;
    }
}

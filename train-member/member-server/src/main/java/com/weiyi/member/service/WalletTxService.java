package com.weiyi.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.IdUtil;
import com.weiyi.member.dto.RechargeCmd;
import com.weiyi.member.dto.WalletDTO;
import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.member.infra.entity.MWallet;
import com.weiyi.member.infra.entity.MWalletFlow;
import com.weiyi.member.infra.mapper.MWalletFlowMapper;
import com.weiyi.member.infra.mapper.MWalletMapper;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.member.dto.WalletFlowDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包资金操作：全部为本地事务，乐观锁防并发超扣，DB CHECK(0~1000) 兜底。
 * 供 WalletController 直接调用，也供订单支付/退票在同一事务内复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTxService {

    /** 钱包余额上限（与 DDL CHECK 约束一致） */
    public static final BigDecimal BALANCE_LIMIT = BigDecimal.valueOf(1000);

    private final MWalletMapper walletMapper;
    private final MWalletFlowMapper flowMapper;
    private final TransactionTemplate transactionTemplate;

    public MWallet getWallet(long memberId) {
        MWallet wallet = walletMapper.selectOne(Wrappers.<MWallet>lambdaQuery()
                .eq(MWallet::getMemberId, memberId));
        if (wallet == null) {
            throw new BusinessException(ErrorCode.WALLET_NOT_EXISTS);
        }
        return wallet;
    }

    @Transactional
    public WalletDTO recharge(RechargeCmd cmd) {
        MWallet wallet = getWallet(cmd.getMemberId());
        if (wallet.getStatus() == null || wallet.getStatus() != 1) {
            throw new BusinessException(ErrorCode.WALLET_FROZEN);
        }
        BigDecimal target = wallet.getBalance().add(cmd.getAmount());
        if (target.compareTo(BALANCE_LIMIT) > 0) {
            BigDecimal quota = BALANCE_LIMIT.subtract(wallet.getBalance()).max(BigDecimal.ZERO);
            throw new BusinessException(ErrorCode.RECHARGE_OVER_LIMIT,
                    "超出余额上限 1000，剩余可充额度 " + quota + " 元");
        }
        wallet.setBalance(target);
        updateWithOptimisticLock(wallet);
        saveFlow(wallet.getMemberId(), IdUtil.nextNo("R"), StatusConsts.FLOW_RECHARGE,
                cmd.getAmount(), target.subtract(cmd.getAmount()), target, "RECHARGE", "充值");
        log.info("钱包充值：member={} 充值 {} 余额 {} → {}", cmd.getMemberId(), cmd.getAmount(),
                target.subtract(cmd.getAmount()), target);
        return toDTO(wallet);
    }

    /** 扣款（支付），返回扣后余额；必须已在外层事务中 */
    public BigDecimal deduct(long memberId, BigDecimal amount, String bizNo, String remark) {
        MWallet wallet = getWallet(memberId);
        if (wallet.getStatus() == null || wallet.getStatus() != 1) {
            throw new BusinessException(ErrorCode.WALLET_FROZEN);
        }
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.WALLET_NOT_ENOUGH);
        }
        BigDecimal before = wallet.getBalance();
        wallet.setBalance(before.subtract(amount));
        updateWithOptimisticLock(wallet);
        saveFlow(memberId, IdUtil.nextNo("P"), StatusConsts.FLOW_PAY,
                amount, before, wallet.getBalance(), bizNo, remark);
        log.info("钱包扣款：member={} 金额 {} 余额 {} → {} bizNo={}", memberId, amount, before, wallet.getBalance(), bizNo);
        return wallet.getBalance();
    }

    /** 入账（退款），返回入账后余额；DB CHECK 超上限时转业务异常 */
    public BigDecimal credit(long memberId, BigDecimal amount, String bizNo, String remark) {
        MWallet wallet = getWallet(memberId);
        BigDecimal before = wallet.getBalance();
        wallet.setBalance(before.add(amount));
        try {
            updateWithOptimisticLock(wallet);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.RECHARGE_OVER_LIMIT, "退款后余额将超上限，请联系客服");
        }
        saveFlow(memberId, IdUtil.nextNo("F"), StatusConsts.FLOW_REFUND,
                amount, before, wallet.getBalance(), bizNo, remark);
        log.info("钱包入账：member={} 金额 {} 余额 {} → {} bizNo={}", memberId, amount, before, wallet.getBalance(), bizNo);
        return wallet.getBalance();
    }

    private void updateWithOptimisticLock(MWallet wallet) {
        int rows = walletMapper.updateById(wallet); // @Version 自动追加 WHERE version=?
        if (rows == 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "钱包操作冲突，请重试");
        }
    }

    private void saveFlow(long memberId, String flowNo, int flowType, BigDecimal amount,
                          BigDecimal before, BigDecimal after, String bizNo, String remark) {
        MWalletFlow flow = new MWalletFlow();
        flow.setMemberId(memberId);
        flow.setFlowNo(flowNo);
        flow.setFlowType(flowType);
        flow.setAmount(amount);
        flow.setBalanceBefore(before);
        flow.setBalanceAfter(after);
        flow.setBizNo(bizNo);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(flow);
    }

    // ==================== 跨域 saga 支撑：幂等 + 冲正 + 对账 ====================

    /** 幂等扣款（支付）：同 bizNo 支付流水已存在 → 原样返回该流水余额，避免确认重试重复扣款 */
    public BigDecimal deductIdempotent(long memberId, BigDecimal amount, String bizNo, String remark) {
        MWalletFlow existing = findByBiz(bizNo, StatusConsts.FLOW_PAY);
        if (existing != null) {
            log.info("钱包扣款幂等命中 bizNo={} 余额={}", bizNo, existing.getBalanceAfter());
            return existing.getBalanceAfter();
        }
        return transactionTemplate.execute(action -> deduct(memberId, amount, bizNo, remark));
    }

    /** 幂等入账（退款）：同 bizNo 退款流水已存在 → 原样返回，重试安全 */
    @Transactional
    public BigDecimal creditIdempotent(long memberId, BigDecimal amount, String bizNo, String remark) {
        MWalletFlow existing = findByBiz(bizNo, StatusConsts.FLOW_REFUND);
        if (existing != null) {
            log.info("钱包退款幂等命中 bizNo={} 余额={}", bizNo, existing.getBalanceAfter());
            return existing.getBalanceAfter();
        }
        return credit(memberId, amount, bizNo, remark);
    }

    /** 冲正（支付确认失败后把已扣款项退回）：独立 bizNo 便于对账 */
    @Transactional
    public BigDecimal compensate(long memberId, BigDecimal amount, String orderNo) {
        log.warn("钱包冲正：member={} orderNo={} 退回 {}", memberId, orderNo, amount);
        return credit(memberId, amount, orderNo + "-RV", "支付冲正：订单确认失败自动退回");
    }

    private MWalletFlow findByBiz(String bizNo, int flowType) {
        return flowMapper.selectOne(Wrappers.<MWalletFlow>lambdaQuery()
                .eq(MWalletFlow::getBizNo, bizNo)
                .eq(MWalletFlow::getFlowType, flowType)
                .last("LIMIT 1"));
    }

    /** 近一段时间内的退款流水（biz_no 即退款批次号，对账扫描用） */
    public List<MWalletFlow> refundFlowsSince(LocalDateTime since) {
        return flowMapper.selectList(Wrappers.<MWalletFlow>lambdaQuery()
                .eq(MWalletFlow::getFlowType, StatusConsts.FLOW_REFUND)
                .ge(MWalletFlow::getCreatedAt, since)
                .orderByAsc(MWalletFlow::getId));
    }

    public PageResult<WalletFlowDTO> flows(long memberId, long pageNum, long pageSize) {
        Page<MWalletFlow> page = flowMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<MWalletFlow>lambdaQuery()
                        .eq(MWalletFlow::getMemberId, memberId)
                        .orderByDesc(MWalletFlow::getId));
        return PageUtils.convert(page, this::toFlowDTO);
    }

    private WalletFlowDTO toFlowDTO(MWalletFlow flow) {
        WalletFlowDTO dto = new WalletFlowDTO();
        dto.setFlowNo(flow.getFlowNo());
        dto.setFlowType(flow.getFlowType());
        dto.setAmount(flow.getAmount());
        dto.setBalanceBefore(flow.getBalanceBefore());
        dto.setBalanceAfter(flow.getBalanceAfter());
        dto.setBizNo(flow.getBizNo());
        dto.setRemark(flow.getRemark());
        dto.setCreatedAt(flow.getCreatedAt());
        return dto;
    }

    public WalletDTO toDTO(MWallet wallet) {
        WalletDTO dto = new WalletDTO();
        dto.setMemberId(wallet.getMemberId());
        dto.setBalance(wallet.getBalance());
        dto.setStatus(wallet.getStatus());
        dto.setRechargeQuota(BALANCE_LIMIT.subtract(wallet.getBalance()).max(BigDecimal.ZERO));
        return dto;
    }
}

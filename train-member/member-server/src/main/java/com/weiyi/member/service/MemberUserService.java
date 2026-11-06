package com.weiyi.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.AesUtil;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.PasswordUtil;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.member.api.dto.MemberAuthDTO;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.api.dto.MemberQuery;
import com.weiyi.member.api.dto.RegisterDTO;
import com.weiyi.member.infra.entity.MPassenger;
import com.weiyi.member.infra.entity.MUser;
import com.weiyi.member.infra.entity.MWallet;
import com.weiyi.member.infra.mapper.MPassengerMapper;
import com.weiyi.member.infra.mapper.MUserMapper;
import com.weiyi.member.infra.mapper.MWalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 会员账号（注册一个事务内完成：建号 + 建"本人"乘车人 + 建零元钱包）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberUserService {

    /** 本人乘车人占位姓名（注册页未填姓名，后续编辑乘车人补全） */
    public static final String SELF_NAME_DEFAULT = "本人";

    private final MUserMapper userMapper;
    private final MWalletMapper walletMapper;
    private final MPassengerMapper passengerMapper;
    private final AgentUserModelProvisioner agentUserModelProvisioner;

    public MemberAuthDTO getAuthByPhone(String phone) {
        MUser user = userMapper.selectOne(Wrappers.<MUser>lambdaQuery().eq(MUser::getPhone, phone));
        if (user == null) {
            return null;
        }
        MemberAuthDTO dto = new MemberAuthDTO();
        dto.setId(user.getId());
        dto.setPhone(user.getPhone());
        dto.setPassword(user.getPassword());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus());
        return dto;
    }

    /** 取会员身份证号（退他人代购票时传给订单域做证件比对） */
    public String getIdCardNo(long memberId) {
        MUser user = userMapper.selectById(memberId);
        return user == null ? null : user.getIdCardNo();
    }

    @Transactional
    public MemberDTO register(RegisterDTO cmd) {
        Long exists = userMapper.selectCount(Wrappers.<MUser>lambdaQuery().eq(MUser::getPhone, cmd.getPhone()));
        BizAssert.isTrue(exists == 0, ErrorCode.PHONE_EXISTS);
        // 身份证查重：库内密文，条件参数需同密文比对（ECB 确定性保证等值可比）
        Long idCardExists = userMapper.selectCount(Wrappers.<MUser>lambdaQuery()
                .eq(MUser::getIdCardNo, AesUtil.encrypt(cmd.getIdCardNo())));
        BizAssert.isTrue(idCardExists == 0, ErrorCode.ID_CARD_EXISTS);
        MUser user = new MUser();
        user.setPhone(cmd.getPhone());
        user.setIdCardNo(cmd.getIdCardNo());
        user.setPassword(PasswordUtil.hash(cmd.getPassword()));
        user.setNickname("乘客" + cmd.getPhone().substring(7));
        user.setStatus(1);
        userMapper.insert(user);

        // 自动创建"本人"乘车人：注册后即可直接给自己购票（姓名暂存占位值）
        MPassenger self = new MPassenger();
        self.setMemberId(user.getId());
        self.setName(SELF_NAME_DEFAULT);
        self.setIdType(1);
        self.setIdCardNo(cmd.getIdCardNo());
        self.setPhone(cmd.getPhone());
        self.setPassengerType(1);
        passengerMapper.insert(self);

        MWallet wallet = new MWallet();
        wallet.setMemberId(user.getId());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setVersion(0);
        wallet.setStatus(1);
        walletMapper.insert(wallet);

        // 提交后初始化 AI 授权行（默认可用、不限额度）；失败不影响注册，agent 侧读路径会补建
        agentUserModelProvisioner.provisionAfterCommit(user.getId());

        log.info("会员注册完成：memberId={} phone={} 昵称={}（已建本人乘车人、零元钱包与 AI 授权行）",
                user.getId(), user.getPhone(), user.getNickname());
        return toDTO(user);
    }

    public MemberDTO getById(long id) {
        MUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        return toDTO(user);
    }

    public PageResult<MemberDTO> page(MemberQuery query) {
        Page<MUser> page = userMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<MUser>lambdaQuery()
                        .like(query.getPhone() != null && !query.getPhone().isBlank(),
                                MUser::getPhone, query.getPhone())
                        .eq(query.getStatus() != null, MUser::getStatus, query.getStatus())
                        .orderByDesc(MUser::getCreatedAt));
        log.info("会员分页查询：phone={} status={} 第 {} 页 命中 {} 条/共 {} 条",
                query.getPhone(), query.getStatus(), query.getPageNum(), page.getRecords().size(), page.getTotal());
        return PageUtils.convert(page, this::toDTO);
    }

    public void updateStatus(long id, int status) {
        MUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        log.info("会员状态变更：memberId={} phone={} {} → {}", id, user.getPhone(), user.getStatus(), status);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /** 修改真实姓名：同步更新昵称与本人乘车人（手机号=登录账号、身份证=实名信息不可改） */
    public void updateProfile(long id, String nickname) {
        BizAssert.notBlank(nickname, ErrorCode.PARAM_ERROR, "姓名不能为空");
        MUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        log.info("会员资料变更：memberId={} 姓名 {} → {}", id, user.getNickname(), nickname.trim());
        user.setNickname(nickname.trim());
        userMapper.updateById(user);
        // 本人乘车人 = 名下证件号与会员一致的记录
        MPassenger self = passengerMapper.selectOne(Wrappers.<MPassenger>lambdaQuery()
                .eq(MPassenger::getMemberId, id)
                .eq(MPassenger::getIdCardNo, user.getIdCardNo())
                .last("limit 1"));
        if (self != null && !nickname.trim().equals(self.getName())) {
            self.setName(nickname.trim());
            passengerMapper.updateById(self);
        }
    }

    /** 重置会员密码的默认值 */
    @Value("${ntrain.user-defaults.member-password}")
    private String memberDefaultPassword;

    /** 重置会员密码为默认值；默认值来自环境变量 MEMBER_DEFAULT_PASSWORD，漏配时提示联系管理员 */
    public void resetPassword(long id) {
        MUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        BizAssert.notBlank(memberDefaultPassword, ErrorCode.PARAM_ERROR, "系统未配置默认密码，请联系系统管理员");
        user.setPassword(PasswordUtil.hash(memberDefaultPassword));
        userMapper.updateById(user);
        log.info("会员密码重置为默认值：memberId={} phone={}", id, user.getPhone());
    }

    private MemberDTO toDTO(MUser user) {
        MemberDTO dto = new MemberDTO();
        dto.setId(user.getId());
        dto.setPhone(user.getPhone());
        dto.setIdCardNo(user.getIdCardNo());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}

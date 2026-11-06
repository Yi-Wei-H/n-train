package com.weiyi.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.AesUtil;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.member.dto.PassengerDTO;
import com.weiyi.member.dto.PassengerSaveCmd;
import com.weiyi.member.infra.entity.MPassenger;
import com.weiyi.member.infra.entity.MUser;
import com.weiyi.member.infra.mapper.MPassengerMapper;
import com.weiyi.member.infra.mapper.MUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 乘车人管理（memberId 由业务服务从登录态注入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassengerService {

    private final MPassengerMapper passengerMapper;
    private final MUserMapper userMapper;

    public List<PassengerDTO> listByMember(long memberId) {
        MUser user = userMapper.selectById(memberId);
        String selfIdCard = user == null ? null : user.getIdCardNo();
        List<PassengerDTO> passengers = passengerMapper.selectList(Wrappers.<MPassenger>lambdaQuery()
                .eq(MPassenger::getMemberId, memberId)
                .orderByAsc(MPassenger::getCreatedAt))
                .stream().map(p -> toDTO(p, selfIdCard)).toList();
        log.debug("乘车人列表查询：memberId={} 命中 {} 人", memberId, passengers.size());
        return passengers;
    }

    public PassengerDTO save(long memberId, PassengerSaveCmd cmd) {
        MPassenger entity;
        if (cmd.getId() == null) {
            // 库内密文等值比对（ECB 确定性保证同明文同密文）
            Long exists = passengerMapper.selectCount(Wrappers.<MPassenger>lambdaQuery()
                    .eq(MPassenger::getMemberId, memberId)
                    .eq(MPassenger::getIdCardNo, AesUtil.encrypt(cmd.getIdCardNo())));
            BizAssert.isTrue(exists == 0, ErrorCode.PASSENGER_EXISTS);
            entity = new MPassenger();
            entity.setMemberId(memberId);
        } else {
            entity = passengerMapper.selectById(cmd.getId());
            BizAssert.notNull(entity, ErrorCode.PASSENGER_NOT_EXISTS);
            BizAssert.equal(entity.getMemberId(), memberId, ErrorCode.PASSENGER_NOT_EXISTS);
        }
        entity.setName(cmd.getName());
        entity.setIdType(cmd.getIdType());
        entity.setIdCardNo(cmd.getIdCardNo());
        entity.setPhone(cmd.getPhone());
        entity.setPassengerType(cmd.getPassengerType());
        if (cmd.getId() == null) {
            passengerMapper.insert(entity);
            log.info("乘车人新增：memberId={} id={} 姓名={} 证件类型={} 旅客类型={}",
                    memberId, entity.getId(), entity.getName(), entity.getIdType(), entity.getPassengerType());
        } else {
            passengerMapper.updateById(entity);
            log.info("乘车人修改：memberId={} id={} 姓名={} 证件类型={} 旅客类型={}",
                    memberId, entity.getId(), entity.getName(), entity.getIdType(), entity.getPassengerType());
        }
        MUser user = userMapper.selectById(memberId);
        return toDTO(entity, user == null ? null : user.getIdCardNo());
    }

    public void delete(long id, long memberId) {
        MPassenger entity = passengerMapper.selectById(id);
        BizAssert.notNull(entity, ErrorCode.PASSENGER_NOT_EXISTS);
        BizAssert.equal(entity.getMemberId(), memberId, ErrorCode.PASSENGER_NOT_EXISTS);
        // 本人乘车人不可删除（证件号与会员证件一致）
        MUser user = userMapper.selectById(memberId);
        if (user != null && user.getIdCardNo() != null && user.getIdCardNo().equals(entity.getIdCardNo())) {
            throw new BusinessException(ErrorCode.PASSENGER_SELF_PROTECTED);
        }
        passengerMapper.deleteById(id);
        log.info("乘车人删除：memberId={} id={} 姓名={}", memberId, id, entity.getName());
    }

    private PassengerDTO toDTO(MPassenger entity, String selfIdCard) {
        PassengerDTO dto = new PassengerDTO();
        dto.setId(entity.getId());
        dto.setMemberId(entity.getMemberId());
        dto.setName(entity.getName());
        dto.setIdType(entity.getIdType());
        dto.setIdCardNo(entity.getIdCardNo());
        dto.setPhone(entity.getPhone());
        dto.setPassengerType(entity.getPassengerType());
        dto.setIsSelf(selfIdCard != null && selfIdCard.equals(entity.getIdCardNo()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}

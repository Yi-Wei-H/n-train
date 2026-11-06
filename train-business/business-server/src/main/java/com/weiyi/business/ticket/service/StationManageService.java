package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 车站管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationManageService {

    private final BStationMapper stationMapper;

    public List<StationDTO> listAll() {
        return stationMapper.selectList(Wrappers.<BStation>lambdaQuery()
                .orderByAsc(BStation::getPinyin)).stream().map(this::toDTO).toList();
    }

    public StationDTO save(StationDTO cmd) {
        assertCodeUnique(cmd.getCode(), null);
        BStation station = new BStation();
        station.setCode(cmd.getCode());
        station.setName(cmd.getName());
        station.setPinyin(cmd.getPinyin() == null ? "" : cmd.getPinyin());
        stationMapper.insert(station);
        log.info("车站新增：id={} code={} name={}", station.getId(), station.getCode(), station.getName());
        return toDTO(station);
    }

    public StationDTO update(StationDTO cmd) {
        BStation station = stationMapper.selectById(cmd.getId());
        BizAssert.notNull(station, ErrorCode.NOT_FOUND);
        assertCodeUnique(cmd.getCode(), station.getId());
        log.info("车站更新：id={} code {} → {} name {} → {}", station.getId(),
                station.getCode(), cmd.getCode(), station.getName(), cmd.getName());
        station.setCode(cmd.getCode());
        station.setName(cmd.getName());
        station.setPinyin(cmd.getPinyin());
        stationMapper.updateById(station);
        return toDTO(station);
    }

    public void delete(long id) {
        stationMapper.deleteById(id);
        log.info("车站删除：id={}", id);
    }

    private void assertCodeUnique(String code, Long excludeId) {
        Long count = stationMapper.selectCount(Wrappers.<BStation>lambdaQuery()
                .eq(BStation::getCode, code)
                .ne(excludeId != null, BStation::getId, excludeId));
        BizAssert.isTrue(count == 0, ErrorCode.PARAM_ERROR, "电报码已存在：" + code);
    }

    private StationDTO toDTO(BStation station) {
        StationDTO dto = new StationDTO();
        dto.setId(station.getId());
        dto.setCode(station.getCode());
        dto.setName(station.getName());
        dto.setPinyin(station.getPinyin());
        return dto;
    }
}

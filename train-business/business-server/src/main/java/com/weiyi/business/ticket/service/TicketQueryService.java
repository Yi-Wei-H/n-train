package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.business.ticket.infra.entity.BDailyTrain;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainTicketMapper;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 余票查询（数据端只做 raw 查询；业务服务侧缓存 getOrLoad，§4 原则 4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketQueryService {

    private final BDailyTrainTicketMapper ticketMapper;
    private final BDailyTrainMapper dailyTrainMapper;
    private final BStationMapper stationMapper;

    public List<StationDTO> stations() {
        return stationMapper.selectList(Wrappers.<BStation>lambdaQuery()
                .orderByAsc(BStation::getPinyin)).stream().map(s -> {
            StationDTO dto = new StationDTO();
            dto.setId(s.getId());
            dto.setCode(s.getCode());
            dto.setName(s.getName());
            dto.setPinyin(s.getPinyin());
            return dto;
        }).toList();
    }

    public List<TicketDTO> queryTickets(LocalDate rideDate, String fromStation, String toStation) {
        List<BDailyTrainTicket> rows = ticketMapper.selectList(Wrappers.<BDailyTrainTicket>lambdaQuery()
                .eq(BDailyTrainTicket::getRideDate, rideDate)
                .eq(BDailyTrainTicket::getFromStation, fromStation)
                .eq(BDailyTrainTicket::getToStation, toStation)
                .orderByAsc(BDailyTrainTicket::getDepartureTime));
        log.info("余票查询：{} {} → {}，命中 {} 行", rideDate, fromStation, toStation, rows.size());
        return assemble(rows);
    }

    /** 单车次实时余票：按日期+车次+区间精确查全部席别行（下单前详情直查） */
    public List<TicketDTO> queryTicketDetail(LocalDate rideDate, String trainCode,
                                             String fromStation, String toStation) {
        List<BDailyTrainTicket> rows = ticketMapper.selectList(Wrappers.<BDailyTrainTicket>lambdaQuery()
                .eq(BDailyTrainTicket::getRideDate, rideDate)
                .eq(BDailyTrainTicket::getTrainCode, trainCode)
                .eq(BDailyTrainTicket::getFromStation, fromStation)
                .eq(BDailyTrainTicket::getToStation, toStation)
                .orderByAsc(BDailyTrainTicket::getSeatType));
        log.info("实时余票详情：{} {} {} → {}，命中 {} 行", rideDate, trainCode, fromStation, toStation, rows.size());
        return assemble(rows);
    }

    private List<TicketDTO> assemble(List<BDailyTrainTicket> rows) {
        if (rows.isEmpty()) {
            log.info("余票组装：无余票记录，返回空列表");
            return List.of();
        }
        LocalDate rideDate = rows.get(0).getRideDate();
        List<String> codes = rows.stream().map(BDailyTrainTicket::getTrainCode).distinct().toList();
        Map<String, BDailyTrain> dailyByCode = dailyTrainMapper.selectList(
                        Wrappers.<BDailyTrain>lambdaQuery()
                                .eq(BDailyTrain::getRideDate, rideDate)
                                .in(BDailyTrain::getTrainCode, codes)).stream()
                .collect(Collectors.toMap(BDailyTrain::getTrainCode, Function.identity()));
        codes.stream().filter(c -> !dailyByCode.containsKey(c))
                .forEach(c -> log.warn("每日车次主记录缺失，车型将展示为空：rideDate={} trainCode={}", rideDate, c));
        Map<String, String> nameByCode = stations().stream()
                .collect(Collectors.toMap(StationDTO::getCode, StationDTO::getName));

        return rows.stream().map(row -> {
            TicketDTO dto = new TicketDTO();
            dto.setRideDate(row.getRideDate());
            dto.setTrainCode(row.getTrainCode());
            BDailyTrain daily = dailyByCode.get(row.getTrainCode());
            dto.setTrainType(daily == null ? null : daily.getTrainType());
            dto.setFromStation(row.getFromStation());
            dto.setFromName(nameByCode.get(row.getFromStation()));
            dto.setToStation(row.getToStation());
            dto.setToName(nameByCode.get(row.getToStation()));
            if (dto.getFromName() == null) {
                log.warn("车站编码无法解析站名：code={}", row.getFromStation());
            }
            if (dto.getToName() == null) {
                log.warn("车站编码无法解析站名：code={}", row.getToStation());
            }
            dto.setDepartureTime(row.getDepartureTime());
            dto.setArrivalTime(row.getArrivalTime());
            if (row.getDepartureTime() != null && row.getArrivalTime() != null) {
                dto.setDurationMinutes(Duration.between(row.getDepartureTime(), row.getArrivalTime()).toMinutes());
            }
            dto.setSeatType(row.getSeatType());
            dto.setRemaining(row.getRemaining());
            dto.setPrice(row.getPrice());
            return dto;
        }).toList();
    }
}

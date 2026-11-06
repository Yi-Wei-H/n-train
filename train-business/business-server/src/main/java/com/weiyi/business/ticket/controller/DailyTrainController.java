package com.weiyi.business.ticket.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.business.api.api.DailyTrainApi;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.business.api.dto.DailyTrainCarriageDTO;
import com.weiyi.business.api.dto.DailyTrainDTO;
import com.weiyi.business.api.dto.DailyTrainQuery;
import com.weiyi.business.api.dto.DailyTrainSeatDTO;
import com.weiyi.business.api.dto.DailyTrainTicketDTO;
import com.weiyi.business.api.dto.GenerateResultDTO;
import com.weiyi.business.ticket.infra.entity.BDailyTrain;
import com.weiyi.business.ticket.infra.entity.BDailyTrainCarriage;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainCarriageMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainTicketMapper;
import com.weiyi.business.ticket.service.DailyTrainGenService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DailyTrainController implements DailyTrainApi {

    private final DailyTrainGenService dailyTrainGenService;
    private final BDailyTrainMapper dailyTrainMapper;
    private final BDailyTrainCarriageMapper dailyCarriageMapper;
    private final BDailyTrainSeatMapper dailySeatMapper;
    private final BDailyTrainTicketMapper dailyTicketMapper;

    @Override
    public PageResult<DailyTrainDTO> page(DailyTrainQuery query) {
        Page<BDailyTrain> page = dailyTrainMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<BDailyTrain>lambdaQuery()
                        .eq(query.getRideDate() != null, BDailyTrain::getRideDate, query.getRideDate())
                        .eq(query.getTrainCode() != null && !query.getTrainCode().isBlank(),
                                BDailyTrain::getTrainCode, query.getTrainCode())
                        .orderByDesc(BDailyTrain::getRideDate)
                        .orderByAsc(BDailyTrain::getDepartureTime));
        return PageUtils.convert(page, this::toDTO);
    }

    @Override
    public GenerateResultDTO generateForDate(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate) {
        return dailyTrainGenService.generateForDate(rideDate);
    }

    @Override
    public void submitGenerate(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate, String trainCode) {
        dailyTrainGenService.submitGenerate(rideDate, trainCode);
    }

    @Override
    public void submitRegenerate(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate, String trainCode) {
        dailyTrainGenService.submitRegenerate(rideDate, trainCode);
    }

    @Override
    public int sweepStatus() {
        return dailyTrainGenService.sweepStatus();
    }

    @Override
    public List<DailyTrainCarriageDTO> listCarriages(LocalDate rideDate, String trainCode) {
        return dailyCarriageMapper.selectList(Wrappers.<BDailyTrainCarriage>lambdaQuery()
                        .eq(BDailyTrainCarriage::getRideDate, rideDate)
                        .eq(trainCode != null && !trainCode.isBlank(), BDailyTrainCarriage::getTrainCode, trainCode)
                        .orderByAsc(BDailyTrainCarriage::getTrainCode)
                        .orderByAsc(BDailyTrainCarriage::getCarriageNo))
                .stream().map(c -> {
                    DailyTrainCarriageDTO dto = new DailyTrainCarriageDTO();
                    dto.setTrainCode(c.getTrainCode());
                    dto.setCarriageNo(c.getCarriageNo());
                    dto.setSeatType(c.getSeatType());
                    dto.setSeatCount(c.getSeatCount());
                    dto.setRowCount(c.getRowCount());
                    return dto;
                }).toList();
    }

    @Override
    public List<DailyTrainSeatDTO> listSeats(LocalDate rideDate, String trainCode, Integer carriageNo) {
        BizAssert.notNull(carriageNo, ErrorCode.PARAM_ERROR, "请选择车厢");
        return dailySeatMapper.selectList(Wrappers.<BDailyTrainSeat>lambdaQuery()
                        .eq(BDailyTrainSeat::getRideDate, rideDate)
                        .eq(trainCode != null && !trainCode.isBlank(), BDailyTrainSeat::getTrainCode, trainCode)
                        .eq(BDailyTrainSeat::getCarriageNo, carriageNo)
                        .orderByAsc(BDailyTrainSeat::getRowNo)
                        .orderByAsc(BDailyTrainSeat::getColNo))
                .stream().map(seat -> {
                    DailyTrainSeatDTO dto = new DailyTrainSeatDTO();
                    dto.setCarriageNo(seat.getCarriageNo());
                    dto.setRowNo(seat.getRowNo());
                    dto.setColNo(seat.getColNo());
                    dto.setSeatType(seat.getSeatType());
                    dto.setSell(seat.getSell());
                    return dto;
                }).toList();
    }

    @Override
    public List<DailyTrainTicketDTO> listTickets(LocalDate rideDate, String trainCode) {
        return dailyTicketMapper.selectList(Wrappers.<BDailyTrainTicket>lambdaQuery()
                        .eq(BDailyTrainTicket::getRideDate, rideDate)
                        .eq(trainCode != null && !trainCode.isBlank(), BDailyTrainTicket::getTrainCode, trainCode)
                        .orderByAsc(BDailyTrainTicket::getTrainCode)
                        .orderByAsc(BDailyTrainTicket::getFromStation)
                        .orderByAsc(BDailyTrainTicket::getToStation)
                        .orderByAsc(BDailyTrainTicket::getSeatType))
                .stream().map(t -> {
                    DailyTrainTicketDTO dto = new DailyTrainTicketDTO();
                    dto.setTrainCode(t.getTrainCode());
                    dto.setFromStation(t.getFromStation());
                    dto.setToStation(t.getToStation());
                    dto.setDepartureTime(t.getDepartureTime());
                    dto.setArrivalTime(t.getArrivalTime());
                    dto.setSeatType(t.getSeatType());
                    dto.setRemaining(t.getRemaining());
                    dto.setPrice(t.getPrice());
                    return dto;
                }).toList();
    }

    private DailyTrainDTO toDTO(BDailyTrain entity) {
        DailyTrainDTO dto = new DailyTrainDTO();
        dto.setId(entity.getId());
        dto.setRideDate(entity.getRideDate());
        dto.setTrainCode(entity.getTrainCode());
        dto.setTrainType(entity.getTrainType());
        dto.setDepartureStation(entity.getDepartureStation());
        dto.setArrivalStation(entity.getArrivalStation());
        dto.setDepartureTime(entity.getDepartureTime());
        dto.setArrivalTime(entity.getArrivalTime());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}

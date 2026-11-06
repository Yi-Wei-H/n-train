package com.weiyi.business.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.business.api.dto.MyTicketDTO;
import com.weiyi.business.order.infra.entity.BOrder;
import com.weiyi.business.order.infra.entity.BTicket;
import com.weiyi.business.order.infra.mapper.BOrderMapper;
import com.weiyi.business.order.infra.mapper.BTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的车票查询（旧版语义）：我买的票 + 他人为我买的票（按本人证件号匹配），近 30 天。
 * 证件号密文由 member 侧传入（本库无会员表）；AES/ECB 确定性加密保证同明文同密文，可等值比对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyTicketService {

    private final BTicketMapper ticketMapper;
    private final BOrderMapper orderMapper;

    public List<MyTicketDTO> listByMember(long memberId, String idCardCipher, Integer status) {
        LocalDate earliest = LocalDate.now().minusDays(30);
        String cipher = (idCardCipher == null || idCardCipher.isBlank()) ? null : idCardCipher;
        if (cipher == null) {
            log.warn("我的车票查询：会员证件号缺失，无法匹配他人代购车票，仅查本人购票：memberId={}", memberId);
        }
        List<BTicket> tickets = ticketMapper.selectList(Wrappers.<BTicket>lambdaQuery()
                .ge(BTicket::getRideDate, earliest)
                .and(w -> w.eq(BTicket::getMemberId, memberId)
                        .or(cipher != null, q -> q.eq(BTicket::getIdCardNo, cipher)))
                .eq(status != null, BTicket::getTicketStatus, status)
                .orderByDesc(BTicket::getDepartureTime));
        log.debug("我的车票查询：memberId={} status={} 命中 {} 张", memberId, status, tickets.size());
        Map<Long, BOrder> orderById = tickets.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(tickets.stream().map(BTicket::getOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(BOrder::getId, Function.identity()));
        return tickets.stream().map(ticket -> {
            MyTicketDTO dto = new MyTicketDTO();
            dto.setId(ticket.getId());
            dto.setOrderId(ticket.getOrderId());
            BOrder order = orderById.get(ticket.getOrderId());
            if (order == null) {
                log.warn("我的车票查询：车票缺少对应订单记录：ticketId={} orderId={}", ticket.getId(), ticket.getOrderId());
            }
            dto.setOrderNo(order == null ? null : order.getOrderNo());
            dto.setOrderItemId(ticket.getOrderItemId());
            dto.setMemberId(ticket.getMemberId());
            dto.setPassengerId(ticket.getPassengerId());
            dto.setPassengerName(ticket.getPassengerName());
            dto.setIdCardNo(ticket.getIdCardNo());
            dto.setRideDate(ticket.getRideDate());
            dto.setTrainCode(ticket.getTrainCode());
            dto.setFromStation(ticket.getFromStation());
            dto.setToStation(ticket.getToStation());
            dto.setDepartureTime(ticket.getDepartureTime());
            dto.setArrivalTime(ticket.getArrivalTime());
            dto.setSeatType(ticket.getSeatType());
            dto.setCarriageNo(ticket.getCarriageNo());
            dto.setSeatNo(ticket.getSeatNo());
            dto.setTicketStatus(ticket.getTicketStatus());
            dto.setPrice(ticket.getPrice());
            return dto;
        }).toList();
    }
}

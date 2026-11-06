package com.weiyi.business.api.api;

import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 余票查询接口（缓存由业务服务侧维护）。
 */
@FeignClient(name = "train-business", contextId = "ticketQueryApi")
public interface TicketQueryApi {

    @GetMapping("/tk/ticket/stations")
    List<StationDTO> stations();

    @GetMapping("/tk/ticket/query")
    List<TicketDTO> queryTickets(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam("fromStation") String fromStation,
            @RequestParam("toStation") String toStation);

    /** 单车次实时余票（该车次该区间全部席别；下单前详情直查，不走缓存） */
    @GetMapping("/tk/ticket/detail")
    List<TicketDTO> queryTicketDetail(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam("trainCode") String trainCode,
            @RequestParam("fromStation") String fromStation,
            @RequestParam("toStation") String toStation);
}

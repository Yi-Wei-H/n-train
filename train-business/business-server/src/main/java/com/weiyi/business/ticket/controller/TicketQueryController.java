package com.weiyi.business.ticket.controller;

import com.weiyi.business.api.api.TicketQueryApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.business.ticket.service.TicketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TicketQueryController implements TicketQueryApi {

    private final TicketQueryService ticketQueryService;

    @Override
    public List<StationDTO> stations() {
        return ticketQueryService.stations();
    }

    @Override
    public List<TicketDTO> queryTickets(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            String fromStation, String toStation) {
        return ticketQueryService.queryTickets(rideDate, fromStation, toStation);
    }

    @Override
    public List<TicketDTO> queryTicketDetail(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            String trainCode, String fromStation, String toStation) {
        return ticketQueryService.queryTicketDetail(rideDate, trainCode, fromStation, toStation);
    }
}

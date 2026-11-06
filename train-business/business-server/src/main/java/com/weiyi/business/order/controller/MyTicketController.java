package com.weiyi.business.order.controller;

import com.weiyi.business.api.api.MyTicketApi;
import com.weiyi.business.api.dto.MyTicketDTO;
import com.weiyi.business.order.service.MyTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MyTicketController implements MyTicketApi {

    private final MyTicketService myTicketService;

    @Override
    public List<MyTicketDTO> listByMember(long memberId, String idCardCipher, Integer status) {
        return myTicketService.listByMember(memberId, idCardCipher, status);
    }
}

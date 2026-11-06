package com.weiyi.business.ticket.controller;

import com.weiyi.business.api.api.SkTokenApi;
import com.weiyi.business.ticket.service.SkTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SkTokenController implements SkTokenApi {

    private final SkTokenService skTokenService;

    @Override
    public int topup() {
        return skTokenService.topup();
    }
}

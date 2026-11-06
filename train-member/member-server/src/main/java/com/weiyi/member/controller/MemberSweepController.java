package com.weiyi.member.controller;

import com.weiyi.member.api.api.MemberSweepApi;
import com.weiyi.member.service.OrderBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/** 巡检端点（train-task 定时经 Feign 触发，网关不对外暴露） */
@RestController
@RequiredArgsConstructor
public class MemberSweepController implements MemberSweepApi {

    private final OrderBizService orderBizService;

    @Override
    public int sweepRefundConfirms() {
        return orderBizService.sweepRefundConfirms();
    }
}

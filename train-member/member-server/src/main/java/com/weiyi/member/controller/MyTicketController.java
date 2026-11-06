package com.weiyi.member.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.AesUtil;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.business.api.api.MyTicketApi;
import com.weiyi.business.api.dto.MyTicketDTO;
import com.weiyi.member.service.MemberUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的车票。
 */
@RestController
@RequestMapping("/my-ticket")
public class MyTicketController {

    private final MyTicketApi myTicketApi;
    private final MemberUserService memberUserService;

    public MyTicketController(MyTicketApi myTicketApi, MemberUserService memberUserService) {
        this.myTicketApi = myTicketApi;
        this.memberUserService = memberUserService;
    }

    @GetMapping("/list")
    public Result<List<MyTicketDTO>> list(
            @RequestParam(required = false) Integer ticketStatus) {
        long memberId = LoginContext.requireMemberId();
        // 身份证转确定性密文传给订单域：匹配"他人为我买的票"（ECB 同明文同密文，URL 不落明文）
        String idCard = memberUserService.getIdCardNo(memberId);
        String idCardCipher = (idCard == null || idCard.isBlank()) ? null : AesUtil.encrypt(idCard);
        return Result.ok(myTicketApi.listByMember(memberId, idCardCipher, ticketStatus));
    }
}

package com.weiyi.business.api.api;

import com.weiyi.business.api.dto.MyTicketDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 我的车票接口（车票随订单域存储）：我买的票 + 他人为我买的票，近 30 天。
 */
@FeignClient(name = "train-business", contextId = "myTicketApi")
public interface MyTicketApi {

    /**
     * 条件一：我下单买的票；条件二：他人为我买的票（乘车人证件号 = 我的证件号，库内密文等值比对）。
     * idCardCipher：当前用户身份证的 AES 确定性密文（member 侧生成，本库无会员表查不到身份证），
     * 不传则只查自己买的。近 30 天（含今日）。
     */
    @GetMapping("/od/my-ticket/list")
    List<MyTicketDTO> listByMember(@RequestParam("memberId") long memberId,
                                   @RequestParam(value = "idCardCipher", required = false) String idCardCipher,
                                   @RequestParam(value = "status", required = false) Integer status);
}

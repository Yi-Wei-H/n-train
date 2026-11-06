package com.weiyi.member.api.api;

import com.weiyi.member.api.dto.MemberPassengerDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 乘车人接口（服务间 RPC）：memberId 由调用方服务端注入，member 侧校验数据归属。
 */
@FeignClient(name = "train-member", contextId = "passengerApi")
public interface PassengerApi {

    /** 查询会员名下全部乘车人 */
    @GetMapping("/rpc/passenger/list")
    List<MemberPassengerDTO> listByMember(@RequestParam("memberId") long memberId);
}

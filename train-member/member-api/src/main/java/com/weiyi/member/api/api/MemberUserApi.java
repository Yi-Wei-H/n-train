package com.weiyi.member.api.api;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.member.api.dto.MemberAuthDTO;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.api.dto.MemberQuery;
import com.weiyi.member.api.dto.RegisterDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会员账号接口（注册在数据端一个事务内完成：建号 + 建钱包）。
 */
@FeignClient(name = "train-member", contextId = "memberUserApi")
public interface MemberUserApi {

    @GetMapping("/mb/user/auth")
    MemberAuthDTO getAuthByPhone(@RequestParam("phone") String phone);

    @PostMapping("/mb/user/register")
    MemberDTO register(@RequestBody RegisterDTO cmd);

    @GetMapping("/mb/user/{id}")
    MemberDTO getById(@PathVariable("id") long id);

    @GetMapping("/mb/user/page")
    PageResult<MemberDTO> page(@SpringQueryMap MemberQuery query);

    @PutMapping("/mb/user/{id}/status")
    void updateStatus(@PathVariable("id") long id, @RequestParam("status") int status);

    /** 重置会员密码为默认值（MEMBER_DEFAULT_PASSWORD，管理端调用） */
    @PutMapping("/mb/user/{id}/password")
    void resetPassword(@PathVariable("id") long id);

    /** 修改个人信息（真实姓名，同步更新本人乘车人） */
    @PutMapping("/mb/user/{id}/profile")
    void updateProfile(@PathVariable("id") long id, @RequestParam("nickname") String nickname);
}

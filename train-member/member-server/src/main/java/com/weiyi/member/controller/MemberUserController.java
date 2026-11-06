package com.weiyi.member.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.member.api.api.MemberUserApi;
import com.weiyi.member.api.dto.MemberAuthDTO;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.api.dto.MemberQuery;
import com.weiyi.member.api.dto.RegisterDTO;
import com.weiyi.member.service.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberUserController implements MemberUserApi {

    private final MemberUserService memberUserService;

    @Override
    public MemberAuthDTO getAuthByPhone(String phone) {
        return memberUserService.getAuthByPhone(phone);
    }

    @Override
    public MemberDTO register(RegisterDTO cmd) {
        return memberUserService.register(cmd);
    }

    @Override
    public MemberDTO getById(long id) {
        return memberUserService.getById(id);
    }

    @Override
    public PageResult<MemberDTO> page(MemberQuery query) {
        return memberUserService.page(query);
    }

    @Override
    public void updateStatus(long id, int status) {
        memberUserService.updateStatus(id, status);
    }

    @Override
    public void resetPassword(long id) {
        memberUserService.resetPassword(id);
    }

    @Override
    public void updateProfile(long id, String nickname) {
        memberUserService.updateProfile(id, nickname);
    }
}

package com.weiyi.member.dto;

import com.weiyi.member.api.dto.MemberDTO;

/** 会员登录结果（JWT token + 会员基础信息） */
public record LoginResult(String token, MemberDTO member) {
}

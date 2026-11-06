package com.weiyi.admin.dto;

/** 管理员登录结果（JWT token + 显示名 + 超管标识） */
public record LoginResult(String token, String nickname, boolean superAdmin) {
}

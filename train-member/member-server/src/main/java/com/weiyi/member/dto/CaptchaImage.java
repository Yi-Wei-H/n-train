package com.weiyi.member.dto;

/** 图形验证码下发结果（imageBase64 带 data:image/png;base64 前缀，前端可直接渲染） */
public record CaptchaImage(String captchaKey, String imageBase64) {
}

package com.weiyi.member.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.weiyi.common.core.util.AesDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 发送短信验证码命令（需先通过图形验证码，防脚本刷短信；手机号传输层 AES 解密） */
@Data
public class SendCodeWebCmd implements Serializable {

    @JsonDeserialize(using = AesDeserializer.class)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaKey;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}

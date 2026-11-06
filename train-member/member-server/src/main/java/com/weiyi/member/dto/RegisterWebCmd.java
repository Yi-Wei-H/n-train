package com.weiyi.member.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.weiyi.common.core.util.AesDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 会员注册命令（图形验证码 + RSA 密文密码 + 身份证实名；手机号/身份证传输层 AES 解密） */
@Data
public class RegisterWebCmd implements Serializable {

    @JsonDeserialize(using = AesDeserializer.class)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @JsonDeserialize(using = AesDeserializer.class)
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCardNo;

    @NotBlank(message = "加密凭证不能为空")
    private String keyId;

    @NotBlank(message = "密码密文不能为空")
    private String cipher;

    @NotBlank(message = "验证码不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    public RegisterCmd toServiceCmd() {
        return new RegisterCmd(phone, idCardNo, keyId, cipher, captchaKey, captchaCode);
    }
}

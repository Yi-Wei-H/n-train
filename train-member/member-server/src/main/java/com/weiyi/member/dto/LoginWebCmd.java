package com.weiyi.member.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.weiyi.common.core.util.AesDeserializer;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/** 会员登录命令（密码/短信双模式：cipher 非空走密码，否则走 smsCode；手机号传输层 AES 解密） */
@Data
public class LoginWebCmd implements Serializable {

    @JsonDeserialize(using = AesDeserializer.class)
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 密码登录用（RSA 密文）；短信登录时不传 */
    private String keyId;

    private String cipher;

    /** 短信登录用；密码登录时不传 */
    private String smsCode;
}

package com.weiyi.member.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import com.weiyi.common.core.util.AesDeserializer;
import com.weiyi.common.core.util.AesSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 注册命令（phone/idCardNo 传输层 AES 加密，入库前已解密） */
@Data
public class RegisterDTO implements Serializable {

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCardNo;

    @NotBlank(message = "密码不能为空")
    private String password;
}

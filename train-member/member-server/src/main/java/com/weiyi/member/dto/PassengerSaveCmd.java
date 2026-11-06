package com.weiyi.member.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import com.weiyi.common.core.util.AesDeserializer;
import com.weiyi.common.core.util.AesSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 乘车人新增/编辑命令（id 为空视为新增；姓名/证件/手机号传输层 AES 加密） */
@Data
public class PassengerSaveCmd implements Serializable {

    private Long id;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotNull(message = "证件类型不能为空")
    private Integer idType;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    @NotBlank(message = "证件号不能为空")
    @Pattern(regexp = "^[0-9A-Za-z]{5,25}$", message = "证件号格式不正确")
    private String idCardNo;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String phone;

    @NotNull(message = "乘车人类型不能为空")
    private Integer passengerType;
}

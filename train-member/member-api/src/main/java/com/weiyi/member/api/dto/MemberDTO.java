package com.weiyi.member.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import com.weiyi.common.core.util.AesDeserializer;
import com.weiyi.common.core.util.AesSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会员对外信息（不含密码；phone/idCardNo 传输层 AES 加密） */
@Data
public class MemberDTO implements Serializable {

    private Long id;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String phone;

    private String nickname;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String idCardNo;

    private Integer status;
    private LocalDateTime createdAt;
}

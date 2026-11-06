package com.weiyi.member.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import com.weiyi.common.core.util.AesDeserializer;
import com.weiyi.common.core.util.AesSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 乘车人（姓名/证件/手机号传输层 AES 加密；展示层自行遮掩） */
@Data
public class PassengerDTO implements Serializable {

    private Long id;
    private Long memberId;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String name;

    /** 1 身份证 2 港澳台 3 护照 */
    private Integer idType;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String idCardNo;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String phone;

    /** 1 成人 2 儿童 3 学生 */
    private Integer passengerType;
    /** 是否本人乘车人（证件号与会员证件一致） */
    private Boolean isSelf;
    private LocalDateTime createdAt;
}

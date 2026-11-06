package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 乘车人（服务间 RPC 契约，明文字段：内网受信调用，展示层脱敏由消费方负责）。
 */
@Data
public class MemberPassengerDTO implements Serializable {

    private Long id;

    private Long memberId;

    private String name;

    /** 1 身份证 2 港澳台 3 护照 */
    private Integer idType;

    private String idCardNo;

    private String phone;

    /** 1 成人 2 儿童 3 学生 */
    private Integer passengerType;

    /** 是否本人乘车人（证件号与会员证件一致） */
    private Boolean isSelf;

    private LocalDateTime createdAt;
}

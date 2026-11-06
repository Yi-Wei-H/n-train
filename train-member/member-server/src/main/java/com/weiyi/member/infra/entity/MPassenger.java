package com.weiyi.member.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weiyi.common.mybatis.handler.AesCryptoTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "m_passenger", autoResultMap = true)
public class MPassenger extends BaseTimeEntity {

    private Long memberId;
    private String name;
    /** 1 身份证 2 港澳台 3 护照 */
    private Integer idType;
    /** 证件号（库内 AES 加密存储，读取自动解密） */
    @TableField(typeHandler = AesCryptoTypeHandler.class)
    private String idCardNo;
    private String phone;
    /** 1 成人 2 儿童 3 学生 */
    private Integer passengerType;

    @TableLogic
    private Integer deleted;
}

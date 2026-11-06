package com.weiyi.member.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weiyi.common.mybatis.handler.AesCryptoTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "m_user", autoResultMap = true)
public class MUser extends BaseTimeEntity {

    /** 登录手机号 */
    private String phone;
    private String password;
    /** 身份证号（库内 AES 加密存储，读取自动解密） */
    @TableField(typeHandler = AesCryptoTypeHandler.class)
    private String idCardNo;
    private String nickname;
    private Integer status;

    @TableLogic
    private Integer deleted;
}

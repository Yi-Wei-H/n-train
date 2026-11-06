package com.weiyi.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/** 修改个人信息命令（真实姓名，服务端同步本人乘车人） */
@Data
public class ProfileWebCmd implements Serializable {

    @NotBlank(message = "姓名不能为空")
    private String nickname;
}

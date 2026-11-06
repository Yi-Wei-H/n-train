package com.weiyi.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 管理员新增/编辑命令（id 为空新增；password 仅新增时必填，编辑改密走重置默认密码） */
@Data
public class AdminSaveCmd implements Serializable {

    private Long id;

    @NotBlank(message = "登录名不能为空")
    private String username;

    @NotBlank(message = "显示名不能为空")
    private String nickname;

    private String phone;
    private String email;
    private Long deptId;
    /** S 超管 / A 普管 */
    private String userType;
    private Integer status;
    /** 原始密码（新增时必填，编辑时为空表示不改） */
    private String password;
    private List<Long> roleIds;
}

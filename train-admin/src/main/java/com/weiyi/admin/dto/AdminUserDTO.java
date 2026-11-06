package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 管理员列表/详情 */
@Data
public class AdminUserDTO implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Long deptId;
    private String deptName;
    private String userType;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    /** 已绑定角色 ID（详情用） */
    private List<Long> roleIds;
}

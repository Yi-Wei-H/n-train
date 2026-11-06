package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 角色 */
@Data
public class RoleDTO implements Serializable {

    private Long id;
    private String roleName;
    private String roleKey;
    private Integer builtIn;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}

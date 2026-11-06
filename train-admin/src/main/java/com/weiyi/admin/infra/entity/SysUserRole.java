package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员-角色关联（联合主键表：无单列 id，只做按条件增删查）。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    private Long userId;
    private Long roleId;
}

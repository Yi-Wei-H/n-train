package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-菜单关联（联合主键表）。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    private Long roleId;
    private Long menuId;
}

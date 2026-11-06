package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseTimeEntity {

    private Long parentId;
    private String menuName;
    /** M 目录 / C 菜单 / F 按钮 */
    private String menuType;
    /** 权限标识，如 b:order:list */
    private String perms;
    private String path;
    private String component;
    private String icon;
    private Integer orderNum;
    private Integer visible;
    private Integer status;

    @TableLogic
    private Integer deleted;
}

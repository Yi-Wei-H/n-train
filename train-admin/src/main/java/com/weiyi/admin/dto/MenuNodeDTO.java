package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 菜单节点（目录 M/菜单 C/按钮 F），树形返回给前端生成动态路由 */
@Data
public class MenuNodeDTO implements Serializable {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String perms;
    private String path;
    private String component;
    private String icon;
    private Integer orderNum;
    private Integer visible;
    private Integer status;
    private List<MenuNodeDTO> children = new ArrayList<>();
}

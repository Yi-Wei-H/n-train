package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 部门节点（ancestors 部门链，数据权限前缀匹配用） */
@Data
public class DeptNodeDTO implements Serializable {

    private Long id;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private Integer orderNum;
    private Integer status;
    private List<DeptNodeDTO> children = new ArrayList<>();
}

package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseTimeEntity {

    private Long parentId;
    /** 部门链，如 0,1,3 */
    private String ancestors;
    private String deptName;
    private Integer orderNum;
    private Integer status;

    @TableLogic
    private Integer deleted;
}

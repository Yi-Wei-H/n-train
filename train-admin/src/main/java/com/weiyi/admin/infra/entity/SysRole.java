package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseTimeEntity {

    private String roleName;
    private String roleKey;
    /** 1 内置不可删改 */
    private Integer builtIn;
    private Integer status;
    private String remark;

    @TableLogic
    private Integer deleted;
}

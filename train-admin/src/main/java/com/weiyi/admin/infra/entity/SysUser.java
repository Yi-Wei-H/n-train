package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseTimeEntity {

    private String username;
    /** BCrypt 密文 */
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Long deptId;
    /** S 超管 / A 普管 */
    private String userType;
    private Integer status;
    private LocalDateTime lastLoginAt;

    @TableLogic
    private Integer deleted;
}

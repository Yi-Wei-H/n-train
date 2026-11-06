package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_train")
public class BTrain extends BaseTimeEntity {

    private String code;
    /** 1 高铁 2 动车 3 普快 4 直达 */
    private Integer trainType;

    @TableLogic
    private Integer deleted;
}

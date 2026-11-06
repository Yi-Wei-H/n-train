package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_station")
public class BStation extends BaseTimeEntity {

    private String code;
    private String name;
    private String pinyin;

    @TableLogic
    private Integer deleted;
}

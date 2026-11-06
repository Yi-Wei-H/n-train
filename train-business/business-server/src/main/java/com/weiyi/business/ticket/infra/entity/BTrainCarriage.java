package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_train_carriage")
public class BTrainCarriage extends BaseTimeEntity {

    private Long trainId;
    private Integer carriageNo;
    private Integer seatType;
    private Integer seatCount;
    private Integer rowCount;

    @TableLogic
    private Integer deleted;
}

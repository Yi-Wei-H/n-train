package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_train_seat")
public class BTrainSeat extends BaseTimeEntity {

    private Long trainId;
    private Integer carriageNo;
    private Integer rowNo;
    /** A/B/C/D/F */
    private String colNo;
    private Integer seatType;
    /** 站序售出位图，0 空闲 1 售出 */
    private String sell;
}

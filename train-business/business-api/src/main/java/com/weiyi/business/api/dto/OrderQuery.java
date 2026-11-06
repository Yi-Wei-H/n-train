package com.weiyi.business.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 订单分页查询（member 查自己带 memberId；admin 查全部不带/带筛选） */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQuery extends PageQuery {

    private Long memberId;
    private Integer orderStatus;
    /** 多状态归组查询（如"已完成"分类含 3/4/5/6），与 orderStatus 二选一，同时传时以本字段为准 */
    private List<Integer> orderStatuses;
    private LocalDate rideDate;
    private String trainCode;
    /** 只查该时间之前创建的（延时关单兜底扫描用） */
    private LocalDateTime createdBefore;
}

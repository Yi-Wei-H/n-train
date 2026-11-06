package com.weiyi.member.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 退票命令（按明细退，可选部分退） */
@Data
public class RefundWebCmd implements Serializable {

    @NotEmpty(message = "请选择要退的明细")
    private List<Long> itemIds;
}

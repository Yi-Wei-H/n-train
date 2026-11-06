package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 操作日志行 */
@Data
public class OperLogDTO implements Serializable {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String params;
    private Integer resultCode;
    private String ip;
    private Integer costMs;
    private LocalDateTime operAt;
}

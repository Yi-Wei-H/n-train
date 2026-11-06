package com.weiyi.agent.server.req;

import lombok.Data;

/** 记忆总开关设置入参（version 冲突提示刷新） */
@Data
public class AUserMemorySettingReq {

    /** 总开关：1开启 2关闭 */
    private Integer enabled;

    /** 当前已知设置版本 */
    private Integer version;
}

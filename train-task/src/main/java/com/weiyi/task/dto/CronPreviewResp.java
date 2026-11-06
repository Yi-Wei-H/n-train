package com.weiyi.task.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** cron 预览结果（中文说明 + 未来 5 次执行时间） */
@Data
public class CronPreviewResp implements Serializable {

    private String desc;

    private List<String> nextTimes;
}

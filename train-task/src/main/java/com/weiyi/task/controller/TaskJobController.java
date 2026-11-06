package com.weiyi.task.controller;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.task.dto.CronJobReq;
import com.weiyi.task.dto.CronJobResp;
import com.weiyi.task.dto.CronPreviewResp;
import com.weiyi.task.dto.JobClassResp;
import com.weiyi.task.job.JobEnum;
import com.weiyi.task.util.CronDescUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Quartz 任务管理台（仅超管，经网关 /api/task/job/** 访问，超管标识由管理端登录签进 JWT）：
 * 列表/白名单/cron 预览/新增/编辑/暂停/恢复/手动执行/删除。
 * 任务不随应用启动注册，全靠此处手动新增（白名单 JobEnum 防任意类名反射执行）。
 */
@Slf4j
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class TaskJobController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Scheduler scheduler;

    // ==================== 查询 ====================

    /** 全部任务（含状态、cron、上次/下次执行时间、中文说明） */
    @GetMapping("/query")
    public Result<List<CronJobResp>> query() {
        LoginContext.requireSuperAdmin();
        List<CronJobResp> jobs = new ArrayList<>();
        try {
            for (String group : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(group))) {
                    List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                    if (triggers == null || triggers.isEmpty() || !(triggers.get(0) instanceof CronTrigger cron)) {
                        continue;
                    }
                    CronJobResp resp = new CronJobResp();
                    resp.setName(jobKey.getName());
                    resp.setGroup(jobKey.getGroup());
                    resp.setCronExpression(cron.getCronExpression());
                    resp.setCronDesc(CronDescUtil.toChinese(cron.getCronExpression()));
                    resp.setDescription(cron.getDescription());
                    resp.setState(scheduler.getTriggerState(cron.getKey()).name());
                    resp.setNextFireTime(format(cron.getNextFireTime()));
                    resp.setPreFireTime(format(cron.getPreviousFireTime()));
                    // 白名单中文名（不在白名单的任务显示类短名）
                    JobEnum jobEnum = JobEnum.fromClassName(jobKey.getName());
                    resp.setCnName(jobEnum == null
                            ? jobKey.getName().substring(jobKey.getName().lastIndexOf('.') + 1)
                            : jobEnum.getDesc());
                    jobs.add(resp);
                }
            }
        } catch (Exception e) {
            log.error("查询定时任务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询定时任务失败");
        }
        return Result.ok(jobs);
    }

    /** 可选任务白名单（新增弹窗下拉） */
    @GetMapping("/class-list")
    public Result<List<JobClassResp>> classList() {
        LoginContext.requireSuperAdmin();
        List<JobClassResp> list = new ArrayList<>();
        for (JobEnum jobEnum : JobEnum.values()) {
            JobClassResp resp = new JobClassResp();
            resp.setName(jobEnum.getJobClass().getName());
            resp.setCode(jobEnum.name());
            resp.setDesc(jobEnum.getDesc());
            resp.setDescription(jobEnum.getDescription());
            resp.setDefaultCron(jobEnum.getDefaultCron());
            list.add(resp);
        }
        return Result.ok(list);
    }

    /** cron 预览：合法性校验 + 中文说明 + 未来 5 次执行时间 */
    @GetMapping("/cron-preview")
    public Result<CronPreviewResp> cronPreview(@RequestParam @NotBlank String cron) {
        LoginContext.requireSuperAdmin();
        checkCron(cron);
        CronPreviewResp resp = new CronPreviewResp();
        resp.setDesc(CronDescUtil.toChinese(cron));
        List<String> nextTimes = new ArrayList<>();
        try {
            CronExpression expression = new CronExpression(cron);
            Date next = new Date();
            for (int i = 0; i < 5; i++) {
                next = expression.getNextValidTimeAfter(next);
                if (next == null) {
                    break;
                }
                nextTimes.add(format(next));
                next = new Date(next.getTime() + 1000);   // +1s 避免取到同一秒
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "cron 表达式非法");
        }
        resp.setNextTimes(nextTimes);
        return Result.ok(resp);
    }

    // ==================== 管理 ====================

    @PostMapping("/add")
    public Result<Void> add(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        JobEnum jobEnum = checkJobClass(req.getName());
        checkCron(req.getCronExpression());
        JobKey jobKey = JobKey.jobKey(req.getName(), req.getGroup());
        try {
            if (scheduler.checkExists(jobKey)) {
                throw new BusinessException(ErrorCode.DATA_CONFLICT, "任务已存在");
            }
            String description = req.getDescription() != null && !req.getDescription().isBlank()
                    ? req.getDescription() : jobEnum.getDescription();
            JobDetail jobDetail = JobBuilder.newJob(jobEnum.getJobClass())
                    .withIdentity(jobKey)
                    .withDescription(description)
                    .build();
            CronTrigger trigger = (CronTrigger) TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(req.getName(), req.getGroup()))
                    .withDescription(description)
                    .withSchedule(CronScheduleBuilder.cronSchedule(req.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("新增定时任务失败: {}", req, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "新增定时任务失败");
        }
        log.info("新增定时任务成功：{}", req.getName());
        return Result.ok();
    }

    /** 修改 cron/描述：按 triggerKey 重建触发器 */
    @PostMapping("/reschedule")
    public Result<Void> reschedule(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        checkCron(req.getCronExpression());
        TriggerKey triggerKey = TriggerKey.triggerKey(req.getName(), req.getGroup());
        try {
            if (!scheduler.checkExists(JobKey.jobKey(req.getName(), req.getGroup()))) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
            }
            CronTrigger trigger = (CronTrigger) TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withDescription(req.getDescription())
                    .withSchedule(CronScheduleBuilder.cronSchedule(req.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            scheduler.rescheduleJob(triggerKey, trigger);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("修改定时任务失败: {}", req, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "修改定时任务失败");
        }
        return Result.ok();
    }

    @PostMapping("/pause")
    public Result<Void> pause(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        operate(req, "pause");
        return Result.ok();
    }

    @PostMapping("/resume")
    public Result<Void> resume(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        operate(req, "resume");
        return Result.ok();
    }

    /** 手动执行一次，不影响原计划 */
    @PostMapping("/run")
    public Result<Void> run(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        operate(req, "run");
        return Result.ok();
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody CronJobReq req) {
        LoginContext.requireSuperAdmin();
        operate(req, "delete");
        return Result.ok();
    }

    // ==================== 内部 ====================

    private void operate(CronJobReq req, String action) {
        JobKey jobKey = JobKey.jobKey(req.getName(), req.getGroup());
        try {
            switch (action) {
                case "pause" -> scheduler.pauseJob(jobKey);
                case "resume" -> scheduler.resumeJob(jobKey);
                case "run" -> scheduler.triggerJob(jobKey);
                case "delete" -> {
                    TriggerKey triggerKey = TriggerKey.triggerKey(req.getName(), req.getGroup());
                    scheduler.pauseTrigger(triggerKey);
                    scheduler.unscheduleJob(triggerKey);
                    scheduler.deleteJob(jobKey);
                }
                default -> throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("任务操作失败 {}: {}", action, req, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务操作失败");
        }
    }

    private JobEnum checkJobClass(String name) {
        JobEnum jobEnum = JobEnum.fromClassName(name);
        BizAssert.notNull(jobEnum, ErrorCode.PARAM_ERROR, "任务不在白名单内");
        return jobEnum;
    }

    private void checkCron(String cron) {
        BizAssert.isTrue(cron != null && CronExpression.isValidExpression(cron),
                ErrorCode.PARAM_ERROR, "cron 表达式非法");
    }

    private String format(Date date) {
        return date == null ? null : TIME.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }
}

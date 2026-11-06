package com.weiyi.task.job;

import com.weiyi.common.core.trace.TraceIdSupport;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * train-task 全部定时任务的统一入口基类（规则 7）：每次调度生成一个 job 级 traceId 写 MDC，
 * 调度结束 finally 清理——同一次调度内所有日志 traceId 一致，两次调度互不相同。
 *
 * <p>新增任务的标准做法与原来一致（写 Job 类 + {@link JobEnum} 加一行），只是 Job 类改为继承本类：
 * <pre>{@code
 * public class XxxJob extends TraceIdJob {
 *     @Override
 *     protected void handle(JobExecutionContext context) { ... }
 * }
 * }</pre>
 * 这样"新增任务自动纳入 trace 覆盖"，且任务里经 Feign 发出的调用会带上同一个 traceId。
 *
 * <p>Quartz 实例化不支持构造注入，子类依旧用字段注入（@Autowired）。
 * 故障隔离（规则 9）：trace 生成/清理异常只 warn，任务处理结果不受影响。
 */
@Slf4j
public abstract class TraceIdJob implements Job {

    @Override
    public final void execute(JobExecutionContext context) {
        try {
            TraceIdSupport.bind(TraceIdSupport.generate());
        } catch (Exception e) {
            log.warn("[trace] 定时任务生成 traceId 失败，本次调度按无标记执行: {}", e.getMessage());
        }
        try {
            handle(context);
        } finally {
            TraceIdSupport.clear();
        }
    }

    /** 任务逻辑：traceId 已就绪，直接写业务日志即可 */
    protected abstract void handle(JobExecutionContext context);
}

package com.weiyi.task.job;

/**
 * 定时任务白名单：任务管理台只允许创建此处登记的任务，防止任意类名反射执行。
 * 任务不随应用启动自动注册，由超管在管理台手动新增（新增定时任务的标准动作：
 * 写 Job 类 + 本枚举加一行，再到管理台"定时任务"页添加）。
 * 任务名 = 类全名（新增下拉的取值），defaultCron 供管理台新增时自动带出。
 */
public enum JobEnum {

    DAILY_TRAIN_GENERATE("每日车次生成", "定时生成未来第 15 天的每日车次、车厢、座位、余票数据",
            DailyTrainGenerateJob.class, "0 30 1 * * ?"),

    DAILY_TRAIN_STATUS("发车状态巡检", "每分钟推进到点车次状态：未发车→运行中→已到达",
            DailyTrainStatusJob.class, "0 * * * * ?"),

    ORDER_CLOSE_SWEEP("关单兜底扫描", "每分钟扫描超时未支付订单强制关单（延迟消息漏网兜底）",
            OrderCloseSweepJob.class, "10 0/20 * * * ?"),

    TRAVEL_BOARDING_SWEEP("上车过站巡检", "车发离乘客上车站后订单/明细/车票批量转已出行",
            TravelBoardingJob.class, "0 0,20,40 * * * ?"),

    TRAVEL_ARRIVAL_SWEEP("下车过站巡检", "车到达乘客下车站后已出行订单批量转已完成",
            TravelArrivalJob.class, "0 10,30,50 * * * ?"),

    REFUND_CONFIRM_SWEEP("退款对账兜底", "每分钟扫描钱包已入账但订单未确认的退款批次重试确认",
            RefundConfirmSweepJob.class, "20 * * * * ?"),

    SK_TOKEN_TOPUP("令牌补偿", "定时扫描令牌耗尽但仍有余票的车次，加量并覆盖缓存（默认每 5 分钟）",
            SkTokenTopupJob.class, "0 */5 * * * ?");

    /** 任务组（全部任务统一 DEFAULT 组） */
    public static final String JOB_GROUP = "DEFAULT";

    private final String desc;
    private final String description;
    private final Class<? extends org.quartz.Job> jobClass;
    private final String defaultCron;

    JobEnum(String desc, String description, Class<? extends org.quartz.Job> jobClass, String defaultCron) {
        this.desc = desc;
        this.description = description;
        this.jobClass = jobClass;
        this.defaultCron = defaultCron;
    }

    public String getDesc() {
        return desc;
    }

    public String getDescription() {
        return description;
    }

    public Class<? extends org.quartz.Job> getJobClass() {
        return jobClass;
    }

    public String getDefaultCron() {
        return defaultCron;
    }

    /** 按任务类全名匹配白名单；不在白名单返回 null */
    public static JobEnum fromClassName(String className) {
        for (JobEnum e : values()) {
            if (e.jobClass.getName().equals(className)) {
                return e;
            }
        }
        return null;
    }
}

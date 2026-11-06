package com.weiyi.task.util;

import java.util.Map;

/**
 * cron 表达式中文说明（旧版逻辑迁移）：覆盖常用模板，其余返回"自定义表达式"。
 * 仅用于展示，不做合法性校验（校验用 CronExpression.isValidExpression）。
 */
public final class CronDescUtil {

    private static final Map<String, String> WEEK = Map.ofEntries(
            Map.entry("1", "日"), Map.entry("2", "一"), Map.entry("3", "二"), Map.entry("4", "三"),
            Map.entry("5", "四"), Map.entry("6", "五"), Map.entry("7", "六"),
            Map.entry("MON", "一"), Map.entry("TUE", "二"), Map.entry("WED", "三"), Map.entry("THU", "四"),
            Map.entry("FRI", "五"), Map.entry("SAT", "六"), Map.entry("SUN", "日"));

    private CronDescUtil() {
    }

    public static String toChinese(String cron) {
        if (cron == null || cron.isBlank()) {
            return "";
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length != 6) {
            return "自定义表达式：" + cron;
        }
        String sec = parts[0];
        String min = parts[1];
        String hour = parts[2];
        String day = parts[3];
        String month = parts[4];
        String week = parts[5];

        // 每N秒：0/N * * * * ?
        if (sec.matches("0/\\d+") && "*".equals(min)) {
            return "每隔" + sec.substring(2) + "秒执行一次";
        }
        // 每秒：* * * * * ?
        if ("*".equals(sec) && "*".equals(min)) {
            return "每秒执行一次";
        }
        // 每N分钟：0 0/N * * * ?
        if (min.matches("0/\\d+") && "*".equals(hour)) {
            return "每隔" + min.substring(2) + "分钟执行一次";
        }
        // 每分钟：0 * * * * ?
        if ("*".equals(min) && "*".equals(hour)) {
            return "每分钟执行一次";
        }
        // 每小时第M分：0 M * * * ?
        if (min.matches("\\d+") && "*".equals(hour)) {
            return "每小时第" + Integer.parseInt(min) + "分钟执行";
        }
        // 每天HH:mm：0 M H * * ?
        if (min.matches("\\d+") && hour.matches("\\d+") && "?".equals(week) && "*".equals(day)) {
            return "每天 " + pad(hour) + ":" + pad(min) + " 执行";
        }
        // 每周X HH:mm：0 M H ? * W
        if (min.matches("\\d+") && hour.matches("\\d+") && "?".equals(day) && !"*".equals(week)) {
            String w = WEEK.get(week.replace("?", ""));
            if (w == null && week.matches("\\d+")) {
                w = WEEK.get(week);
            }
            if (w != null) {
                return "每周" + w + " " + pad(hour) + ":" + pad(min) + " 执行";
            }
        }
        return "自定义表达式：" + cron;
    }

    private static String pad(String v) {
        return String.format("%2s", v).replace(' ', '0');
    }
}

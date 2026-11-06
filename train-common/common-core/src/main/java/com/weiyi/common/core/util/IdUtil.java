package com.weiyi.common.core.util;

/**
 * 雪花 ID。位分配：1 符号位 + 41 时间戳 + 10 机器位 + 12 序列位。
 * workerId 取 NTRAIN_WORKER_ID 环境变量（0~1023），未配置时按本机 IP 稳定推导。
 */
public final class IdUtil {

    private static final long EPOCH = 1735689600000L; // 2025-01-01

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static volatile Snowflake INSTANCE;

    private IdUtil() {
    }

    public static long nextId() {
        return instance().next();
    }

    /** 业务单号：前缀 + 雪花ID，全局唯一且含时间趋势 */
    public static String nextNo(String prefix) {
        return prefix + instance().next();
    }

    private static Snowflake instance() {
        if (INSTANCE == null) {
            synchronized (IdUtil.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Snowflake(resolveWorkerId());
                }
            }
        }
        return INSTANCE;
    }

    private static long resolveWorkerId() {
        String configured = System.getenv("NTRAIN_WORKER_ID");
        if (configured != null && !configured.isBlank()) {
            long id = Long.parseLong(configured.trim());
            if (id < 0 || id > MAX_WORKER_ID) {
                throw new IllegalArgumentException("NTRAIN_WORKER_ID 必须在 0~" + MAX_WORKER_ID + " 之间");
            }
            return id;
        }
        try {
            byte[] ip = java.net.InetAddress.getLocalHost().getAddress();
            return ((ip[ip.length - 2] & 0x03) << 8) | (ip[ip.length - 1] & 0xFF);
        } catch (Exception e) {
            return (long) (Math.random() * MAX_WORKER_ID);
        }
    }

    private static final class Snowflake {

        private long workerId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        private Snowflake(long workerId) {
            this.workerId = workerId;
        }

        private synchronized long next() {
            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp) {
                timestamp = lastTimestamp; // 时钟回拨：等待追赶
            }
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    while ((timestamp = System.currentTimeMillis()) <= lastTimestamp) {
                        // 自旋到下一毫秒
                    }
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
        }
    }
}

package com.weiyi.agent.server.ai.tool;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话工作记忆——业务实体（Redis 键见 RedisKeys.memoryEntity）。
 * 查询条件与车票快照由服务端在工具执行点写入；待确认单仅为 a_ai_operation 的镜像。
 * 新会话/翻篇不继承，键 TTL 过期整体失效。
 */
@Data
public class WorkMemory {

    /** 最近一次有效查询条件 */
    private LastQuery lastQuery;

    /** 最近一次查询的车票列表（瘦身副本，附查询时间，不视为永久实时事实） */
    private TicketListSnapshot lastTickets;

    /** 待确认单镜像（权威在 a_ai_operation） */
    private PendingConfirm pendingConfirm;

    @Data
    public static class LastQuery {
        private String fromStationCode;
        private String toStationCode;
        private String fromName;
        private String toName;
        private String rideDate;
    }

    @Data
    public static class TicketListSnapshot {
        private String listToken;
        private String queriedAt;
        private List<TicketItemSnapshot> items = new ArrayList<>();
    }

    @Data
    public static class TicketItemSnapshot {
        private Integer index;
        private String ticketRef;
        private String trainCode;
        private String fromStation;
        private String fromName;
        private String toStation;
        private String toName;
        private String rideDate;
        private String departureTime;
        private String arrivalTime;
        private List<SeatSnapshot> seats = new ArrayList<>();
    }

    @Data
    public static class SeatSnapshot {
        private Integer seatType;
        private String seatTypeName;
        private BigDecimal price;
        private Integer count;
    }

    @Data
    public static class PendingConfirm {
        private Long operationId;
        private String confirmId;
        private Integer type;
        private Integer summaryVersion;
        private String summary;
        private String expiresAt;
    }
}

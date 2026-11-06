package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.constant.ToolConstants;
import com.weiyi.business.api.api.TicketQueryApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查票只读工具（QUERY_FILLING / BUY_AGENT 分支白名单）：站点标准化、余票查询、单车次实时余票。
 * 查询结果瘦身后返回模型，同时写入工作记忆并签发短期引用（ticketRef/listToken）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketQueryTools {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TicketQueryApi ticketQueryApi;
    private final ToolRefManager refManager;
    private final WorkMemoryStore memoryStore;

    @Tool(name = "queryStations",
            description = "按关键词查询车站（站名/拼音/车站代码模糊匹配），用于站名纠错与车站代码标准化；后续查询车票须使用返回的 stationCode")
    public String queryStations(@ToolParam(description = "站点关键词：站名、拼音或车站代码") String keyword) {
        log.info("[tool] queryStations keyword={}", keyword);
        if (keyword == null || keyword.isBlank()) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "请提供站点关键词").render();
        }
        String kw = keyword.trim();
        List<Map<String, Object>> hits = ticketQueryApi.stations().stream()
                .filter(station -> matchStation(station, kw))
                .limit(8)
                .map(station -> Map.<String, Object>of(
                        "stationCode", station.getCode(),
                        "stationName", station.getName(),
                        "pinyin", station.getPinyin()))
                .toList();
        if (hits.isEmpty()) {
            return ToolResult.fail(ToolResult.CODE_NOT_FOUND, "未找到匹配车站，请检查站名后重试").render();
        }
        String msg = hits.size() == 1
                ? "唯一匹配车站，查询车票时请使用该 stationCode"
                : "找到多个候选车站，请让用户选择其一，查询时使用对应 stationCode，不得擅自替用户选择";
        return ToolResult.ok(hits, msg).render();
    }

    @Tool(name = "queryTickets",
            description = "按乘车日期与出发/到达站查询可购车次列表（含各席别余票与票价）。缺日期或站点未标准化时先追问用户或调用 queryStations")
    public String queryTickets(
            @ToolParam(description = "出发站代码（queryStations 返回的 stationCode）") String fromStationCode,
            @ToolParam(description = "到达站代码（queryStations 返回的 stationCode）") String toStationCode,
            @ToolParam(description = "乘车日期，格式 yyyy-MM-dd") String rideDate,
            ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] queryTickets userId={} conversationId={} from={} to={} date={}",
                ctx.userId(), ctx.conversationId(), fromStationCode, toStationCode, rideDate);
        LocalDate date = parseDate(rideDate);
        if (date == null) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "乘车日期格式应为 yyyy-MM-dd").render();
        }
        if (date.isBefore(LocalDate.now())) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "乘车日期不能早于今天").render();
        }
        if (fromStationCode != null && fromStationCode.equalsIgnoreCase(toStationCode)) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "出发站与到达站不能相同").render();
        }
        List<StationDTO> stations = ticketQueryApi.stations();
        StationDTO from = resolveStation(stations, fromStationCode);
        StationDTO to = resolveStation(stations, toStationCode);
        if (from == null || to == null) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID,
                    "站点无法识别或存在多个同名车站，请先调用 queryStations 确认车站代码").render();
        }
        List<TicketDTO> rows;
        try {
            rows = ticketQueryApi.queryTickets(date, from.getCode(), to.getCode());
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "车票查询失败，请稍后重试").render();
        }

        // 余票行按席别一条，按车次聚合后生成带序号的列表项与短期引用
        Map<String, List<TicketDTO>> byTrain = new LinkedHashMap<>();
        rows.forEach(row -> byTrain.computeIfAbsent(row.getTrainCode(), k -> new ArrayList<>()).add(row));
        List<WorkMemory.TicketItemSnapshot> snapshots = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (List<TicketDTO> seatRows : byTrain.values().stream()
                .sorted(Comparator.comparing(list -> list.get(0).getDepartureTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            if (index > ToolConstants.TICKET_LIST_LIMIT) {
                break;
            }
            seatRows.sort(Comparator.comparing(TicketDTO::getSeatType, Comparator.nullsLast(Comparator.naturalOrder())));
            TicketDTO first = seatRows.get(0);
            WorkMemory.TicketItemSnapshot snapshot = new WorkMemory.TicketItemSnapshot();
            snapshot.setIndex(index);
            snapshot.setTrainCode(first.getTrainCode());
            snapshot.setFromStation(from.getCode());
            snapshot.setFromName(first.getFromName());
            snapshot.setToStation(to.getCode());
            snapshot.setToName(first.getToName());
            snapshot.setRideDate(date.toString());
            snapshot.setDepartureTime(first.getDepartureTime() == null ? null : first.getDepartureTime().format(HH_MM));
            snapshot.setArrivalTime(first.getArrivalTime() == null ? null : first.getArrivalTime().format(HH_MM));
            seatRows.forEach(row -> {
                WorkMemory.SeatSnapshot seat = new WorkMemory.SeatSnapshot();
                seat.setSeatType(row.getSeatType());
                seat.setSeatTypeName(ToolConstants.seatName(row.getSeatType()));
                seat.setPrice(row.getPrice());
                seat.setCount(row.getRemaining());
                snapshot.getSeats().add(seat);
            });
            snapshot.setTicketRef(refManager.save(ctx.conversationId(), ctx.userId(),
                    ToolConstants.REF_KIND_TICKET, Map.of(
                            "trainCode", first.getTrainCode(),
                            "fromStation", from.getCode(),
                            "toStation", to.getCode(),
                            "fromName", first.getFromName(),
                            "toName", first.getToName(),
                            "rideDate", date.toString())));
            snapshots.add(snapshot);
            items.add(modelItem(snapshot));
            index++;
        }

        String listToken = refManager.save(ctx.conversationId(), ctx.userId(), ToolConstants.REF_KIND_LIST,
                Map.of("fromStationCode", from.getCode(), "toStationCode", to.getCode(), "rideDate", date.toString()));
        WorkMemory memory = memoryStore.getMemory(ctx.conversationId());
        WorkMemory.LastQuery lastQuery = new WorkMemory.LastQuery();
        lastQuery.setFromStationCode(from.getCode());
        lastQuery.setToStationCode(to.getCode());
        lastQuery.setFromName(first0(from, rows));
        lastQuery.setToName(to0(from, rows));
        lastQuery.setRideDate(date.toString());
        memory.setLastQuery(lastQuery);
        WorkMemory.TicketListSnapshot listSnapshot = new WorkMemory.TicketListSnapshot();
        listSnapshot.setListToken(listToken);
        listSnapshot.setQueriedAt(LocalDateTime.now().toString());
        listSnapshot.setItems(snapshots);
        memory.setLastTickets(listSnapshot);
        memoryStore.saveMemory(ctx.conversationId(), memory);

        if (items.isEmpty()) {
            return ToolResult.ok(List.of(), "该日期此区间暂无可购车次").render();
        }
        return ToolResult.ok(items, "查询成功，共 " + items.size() + " 个车次（含各席别余票与票价），用户选择后用 ticketRef 购票").render();
    }

    @Tool(name = "queryTicketDetail",
            description = "按车票引用查询单车次实时席别余票（购票前复核用）；不产生新引用，不替代 prepareBuy 的最终复检")
    public String queryTicketDetail(@ToolParam(description = "车票列表中的车票引用 ticketRef") String ticketRef,
                                    ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] queryTicketDetail userId={} conversationId={} ticketRef={}",
                ctx.userId(), ctx.conversationId(), ticketRef);
        Map<String, Object> payload = refManager.resolve(ctx.conversationId(), ctx.userId(),
                ticketRef, ToolConstants.REF_KIND_TICKET);
        if (payload == null) {
            return ToolResult.fail(ToolResult.CODE_REF_INVALID, "车票引用无效或已过期，请重新查询车票").render();
        }
        LocalDate date = parseDate((String) payload.get("rideDate"));
        if (date == null) {
            return ToolResult.fail(ToolResult.CODE_REF_INVALID, "车票引用无效或已过期，请重新查询车票").render();
        }
        List<TicketDTO> rows;
        try {
            rows = ticketQueryApi.queryTicketDetail(date,
                    (String) payload.get("trainCode"), (String) payload.get("fromStation"), (String) payload.get("toStation"));
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "余票查询失败，请稍后重试").render();
        }
        List<Map<String, Object>> seats = rows.stream()
                .sorted(Comparator.comparing(TicketDTO::getSeatType, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> Map.<String, Object>of(
                        "seatType", row.getSeatType(),
                        "seatTypeName", ToolConstants.seatName(row.getSeatType()),
                        "price", row.getPrice(),
                        "count", row.getRemaining()))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketRef", ticketRef);
        data.put("trainCode", payload.get("trainCode"));
        data.put("fromStation", payload.get("fromName"));
        data.put("toStation", payload.get("toName"));
        data.put("rideDate", payload.get("rideDate"));
        data.put("seats", seats);
        return ToolResult.ok(data, seats.isEmpty() ? "该车次已无可售席别" : "实时余票如下（最终以预检复检为准）").render();
    }

    /** 站名/拼音/代码模糊匹配 */
    private static boolean matchStation(StationDTO station, String keyword) {
        if (station.getCode() != null && station.getCode().equalsIgnoreCase(keyword)) {
            return true;
        }
        if (station.getName() != null && station.getName().contains(keyword)) {
            return true;
        }
        return station.getPinyin() != null && station.getPinyin().toLowerCase().contains(keyword.toLowerCase());
    }

    /** 代码精确命中或名称/拼音唯一命中；多候选/未命中返回 null（消歧交给用户） */
    private static StationDTO resolveStation(List<StationDTO> stations, String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return null;
        }
        String keyword = codeOrName.trim();
        for (StationDTO station : stations) {
            if (keyword.equalsIgnoreCase(station.getCode())) {
                return station;
            }
        }
        List<StationDTO> hits = stations.stream()
                .filter(station -> station.getName() != null && station.getName().contains(keyword))
                .toList();
        return hits.size() == 1 ? hits.get(0) : null;
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 模型视图：站名展示、代码与引用外的内部字段不外泄 */
    private static Map<String, Object> modelItem(WorkMemory.TicketItemSnapshot snapshot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", snapshot.getIndex());
        item.put("ticketRef", snapshot.getTicketRef());
        item.put("trainCode", snapshot.getTrainCode());
        item.put("fromStation", snapshot.getFromName());
        item.put("toStation", snapshot.getToName());
        item.put("rideDate", snapshot.getRideDate());
        item.put("departureTime", snapshot.getDepartureTime());
        item.put("arrivalTime", snapshot.getArrivalTime());
        item.put("seats", snapshot.getSeats().stream().map(seat -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("seatType", seat.getSeatType());
            view.put("seatTypeName", seat.getSeatTypeName());
            view.put("price", seat.getPrice());
            view.put("count", seat.getCount());
            return view;
        }).toList());
        return item;
    }

    private static String first0(StationDTO from, List<TicketDTO> rows) {
        return rows.isEmpty() ? from.getName() : rows.get(0).getFromName();
    }

    private static String to0(StationDTO from, List<TicketDTO> rows) {
        return rows.isEmpty() ? from.getName() : rows.get(0).getToName();
    }
}

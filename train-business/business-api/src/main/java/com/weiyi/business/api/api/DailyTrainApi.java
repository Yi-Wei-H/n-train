package com.weiyi.business.api.api;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.business.api.dto.DailyTrainCarriageDTO;
import com.weiyi.business.api.dto.DailyTrainDTO;
import com.weiyi.business.api.dto.DailyTrainQuery;
import com.weiyi.business.api.dto.DailyTrainSeatDTO;
import com.weiyi.business.api.dto.DailyTrainTicketDTO;
import com.weiyi.business.api.dto.GenerateResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日车次接口：物化生成（幂等，重复生成跳过已有），train-task 服务 Quartz 每日调用。
 */
@FeignClient(name = "train-business", contextId = "dailyTrainApi")
public interface DailyTrainApi {

    @GetMapping("/tk/daily-train/page")
    PageResult<DailyTrainDTO> page(@SpringQueryMap DailyTrainQuery query);

    /** 生成指定日期的全部每日车次（车厢/座位/余票票价），幂等 */
    @PostMapping("/tk/daily-train/generate")
    GenerateResultDTO generateForDate(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate);

    /** 提交异步生成：trainCode 省略=该日全部（幂等跳过已有）；必要校验通过即返回，后台执行 */
    @PostMapping("/tk/daily-train/generate/submit")
    void submitGenerate(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(value = "trainCode", required = false) String trainCode);

    /** 提交异步重新生成：删该车次该日旧数据后重建，必须指定车次；已售出的旧车票不随之失效 */
    @PostMapping("/tk/daily-train/regenerate/submit")
    void submitRegenerate(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam("trainCode") String trainCode);

    /** 到点状态扫描：未发车→运行中→已到达（train-task 的 Quartz 每分钟调用），返回更新行数 */
    @PostMapping("/tk/daily-train/sweep-status")
    int sweepStatus();

    /** 某日车次的车厢列表（trainCode 不传=该日全部车次，座位查询页的车厢下拉） */
    @GetMapping("/tk/daily-train/carriage/list")
    List<DailyTrainCarriageDTO> listCarriages(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(value = "trainCode", required = false) String trainCode);

    /** 某日某车次某车厢的座位列表（含售票位图） */
    @GetMapping("/tk/daily-train/seat/list")
    List<DailyTrainSeatDTO> listSeats(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(value = "trainCode", required = false) String trainCode,
            @RequestParam("carriageNo") Integer carriageNo);

    /** 某日车次的区间余票明细（trainCode 不传=该日全部车次） */
    @GetMapping("/tk/daily-train/ticket/list")
    List<DailyTrainTicketDTO> listTickets(
            @RequestParam("rideDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(value = "trainCode", required = false) String trainCode);
}

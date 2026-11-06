package com.weiyi.member.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.business.api.api.TrainStationApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.business.api.dto.TrainStationDTO;
import com.weiyi.member.service.TicketBizService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 查票（业务侧缓存）。
 */
@RestController
@RequestMapping("/ticket")
public class TicketController {

    private final TicketBizService ticketBizService;
    private final TrainStationApi trainStationApi;

    public TicketController(TicketBizService ticketBizService, TrainStationApi trainStationApi) {
        this.ticketBizService = ticketBizService;
        this.trainStationApi = trainStationApi;
    }

    /** 车次经停站时刻表（用户端展示） */
    @GetMapping("/train-stops")
    @RateLimit(dimension = LimitDimension.IP, count = 60, interval = 60, message = "请求过于频繁，请稍后再试")
    public Result<List<TrainStationDTO>> trainStops(@RequestParam String trainCode) {
        return Result.ok(trainStationApi.listByTrainCode(trainCode));
    }

    @GetMapping("/stations")
    public Result<List<StationDTO>> stations() {
        return Result.ok(ticketBizService.stations());
    }

    @GetMapping("/query")
    @RateLimit(dimension = LimitDimension.IP, count = 300, interval = 60 , message = "查询太频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.USER, count = 300, interval = 60, message = "查询太频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.DEVICE, count = 120, interval = 60, message = "查询太频繁，请稍后再试")
    public Result<List<TicketDTO>> query(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam String fromStation,
            @RequestParam String toStation) {
        return Result.ok(ticketBizService.query(rideDate, fromStation, toStation));
    }

    /** 下单前实时余票（绕过缓存直查数据端）：进入购票详情页时刷新，返回该车次全部席别 */
    @GetMapping("/detail")
    @RateLimit(dimension = LimitDimension.DEVICE, count = 60, interval = 60, message = "查询太频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.IP, count = 60, interval = 60, message = "查询太频繁，请稍后再试")
    public Result<List<TicketDTO>> detail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam String trainCode,
            @RequestParam String fromStation,
            @RequestParam String toStation) {
        return Result.ok(ticketBizService.queryDetail(rideDate, trainCode, fromStation, toStation));
    }
}

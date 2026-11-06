package com.weiyi.admin.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.business.api.api.DailyTrainApi;
import com.weiyi.business.api.api.OrderApi;
import com.weiyi.business.api.dto.DailyTrainCarriageDTO;
import com.weiyi.business.api.dto.DailyTrainDTO;
import com.weiyi.business.api.dto.DailyTrainQuery;
import com.weiyi.business.api.dto.DailyTrainSeatDTO;
import com.weiyi.business.api.dto.DailyTrainTicketDTO;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.OrderQuery;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.admin.operlog.OperLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日车次 + 订单管理。
 */
@Slf4j
@RestController
public class AdminBizController {

    private final DailyTrainApi dailyTrainApi;
    private final OrderApi orderApi;

    public AdminBizController(DailyTrainApi dailyTrainApi, OrderApi orderApi) {
        this.dailyTrainApi = dailyTrainApi;
        this.orderApi = orderApi;
    }

    @GetMapping("/daily-train/page")
    @RequiresPermissions("b:daily:list")
    public Result<PageResult<DailyTrainDTO>> dailyPage(@SpringQueryMap DailyTrainQuery query) {
        return Result.ok(dailyTrainApi.page(query));
    }

    /** 手动生成每日车次（幂等；trainCode 省略=该日全部，定时任务每天自动跑）。异步提交，完成后请刷新列表查看 */
    @PostMapping("/daily-train/generate")
    @RequiresPermissions("b:daily:gen")
    @OperLog(module = "每日车次", operation = "生成每日车次")
    public Result<Void> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(required = false) String trainCode) {
        log.info("管理端提交每日车次生成：operator={} rideDate={} trainCode={}",
                LoginContext.requireAdminId(), rideDate, trainCode);
        dailyTrainApi.submitGenerate(rideDate, trainCode);
        return Result.ok();
    }

    /** 重新生成某车次某日（删旧重建，必须指定车次；旧车次已购车票无法失效，前端需二次确认） */
    @PostMapping("/daily-train/regenerate")
    @RequiresPermissions("b:daily:gen")
    @OperLog(module = "每日车次", operation = "重新生成车次数据")
    public Result<Void> regenerate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam String trainCode) {
        log.info("管理端提交每日车次重新生成：operator={} rideDate={} trainCode={}",
                LoginContext.requireAdminId(), rideDate, trainCode);
        dailyTrainApi.submitRegenerate(rideDate, trainCode);
        return Result.ok();
    }

    @GetMapping("/daily-train/carriage/list")
    @RequiresPermissions("b:daily:seat")
    public Result<List<DailyTrainCarriageDTO>> listCarriages(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(required = false) String trainCode) {
        return Result.ok(dailyTrainApi.listCarriages(rideDate, trainCode));
    }

    @GetMapping("/daily-train/seat/list")
    @RequiresPermissions("b:daily:seat")
    public Result<List<DailyTrainSeatDTO>> listSeats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(required = false) String trainCode,
            @RequestParam Integer carriageNo) {
        return Result.ok(dailyTrainApi.listSeats(rideDate, trainCode, carriageNo));
    }

    @GetMapping("/daily-train/ticket/list")
    @RequiresPermissions("b:daily:ticket")
    public Result<List<DailyTrainTicketDTO>> listTickets(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rideDate,
            @RequestParam(required = false) String trainCode) {
        return Result.ok(dailyTrainApi.listTickets(rideDate, trainCode));
    }

    @GetMapping("/order/page")
    @RequiresPermissions("b:order:list")
    public Result<PageResult<OrderDTO>> orderPage(@SpringQueryMap OrderQuery query) {
        return Result.ok(orderApi.page(query));
    }

    @GetMapping("/order/{orderId}")
    @RequiresPermissions("b:order:list")
    public Result<OrderDTO> orderDetail(@PathVariable long orderId) {
        return Result.ok(orderApi.detail(orderId, null));
    }

    /** 发车：订单/明细/车票联动转已出行 */
    @PostMapping("/order/{orderId}/traveled")
    @RequiresPermissions("b:order:travel")
    @OperLog(module = "订单管理", operation = "标记订单已出行")
    public Result<Void> markTraveled(@PathVariable long orderId) {
        log.info("管理端手动发车（已出行）：operator={} orderId={}", LoginContext.requireAdminId(), orderId);
        orderApi.markTraveled(orderId);
        return Result.ok();
    }

    @PostMapping("/order/{orderId}/complete")
    @RequiresPermissions("b:order:travel")
    @OperLog(module = "订单管理", operation = "标记订单已完成")
    public Result<Void> complete(@PathVariable long orderId) {
        log.info("管理端手动完成订单：operator={} orderId={}", LoginContext.requireAdminId(), orderId);
        orderApi.complete(orderId);
        return Result.ok();
    }
}

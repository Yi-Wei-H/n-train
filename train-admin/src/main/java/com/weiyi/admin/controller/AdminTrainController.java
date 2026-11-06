package com.weiyi.admin.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.business.api.api.StationApi;
import com.weiyi.business.api.api.TrainApi;
import com.weiyi.business.api.api.TrainStationApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TrainDTO;
import com.weiyi.business.api.dto.TrainQuery;
import com.weiyi.business.api.dto.TrainStationDTO;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.admin.operlog.OperLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础数据管理：车次 / 车厢 / 座位 / 车站 / 经停站。
 */
@Slf4j
@RestController
public class AdminTrainController {

    private final TrainApi trainApi;
    private final StationApi stationApi;
    private final TrainStationApi trainStationApi;

    public AdminTrainController(TrainApi trainApi, StationApi stationApi, TrainStationApi trainStationApi) {
        this.trainApi = trainApi;
        this.stationApi = stationApi;
        this.trainStationApi = trainStationApi;
    }

    // ==================== 车次 ====================

    @GetMapping("/train/page")
    @RequiresPermissions("b:train:list")
    public Result<PageResult<TrainDTO>> trainPage(@SpringQueryMap TrainQuery query) {
        return Result.ok(trainApi.page(query));
    }

    @GetMapping("/train/{id}")
    @RequiresPermissions("b:train:list")
    public Result<TrainDTO> trainDetail(@PathVariable long id) {
        return Result.ok(trainApi.detail(id));
    }

    @PostMapping("/train")
    @RequiresPermissions("b:train:add")
    @OperLog(module = "车次管理", operation = "新增车次")
    public Result<TrainDTO> trainSave(@RequestBody TrainDTO train) {
        log.info("管理端新增车次模板：operator={} code={} 车厢 {} 节", LoginContext.requireAdminId(),
                train.getCode(), train.getCarriages() == null ? 0 : train.getCarriages().size());
        return Result.ok(trainApi.save(train));
    }

    @PutMapping("/train")
    @RequiresPermissions("b:train:edit")
    @OperLog(module = "车次管理", operation = "编辑车次")
    public Result<TrainDTO> trainUpdate(@RequestBody TrainDTO train) {
        log.info("管理端修改车次模板：operator={} id={} code={}", LoginContext.requireAdminId(),
                train.getId(), train.getCode());
        return Result.ok(trainApi.update(train));
    }

    @DeleteMapping("/train/{id}")
    @RequiresPermissions("b:train:delete")
    @OperLog(module = "车次管理", operation = "删除车次")
    public Result<Void> trainDelete(@PathVariable long id) {
        log.info("管理端删除车次模板：operator={} id={}", LoginContext.requireAdminId(), id);
        trainApi.delete(id);
        return Result.ok();
    }

    @PostMapping("/train/{id}/generate-seats")
    @RequiresPermissions("b:train:edit")
    @OperLog(module = "车次管理", operation = "生成车次座位")
    public Result<Integer> generateSeats(@PathVariable long id) {
        log.info("管理端生成模板座位：operator={} trainId={}", LoginContext.requireAdminId(), id);
        Integer generated = trainApi.generateSeats(id);
        log.info("管理端生成模板座位完成：trainId={} 生成 {} 个座位", id, generated);
        return Result.ok(generated);
    }

    // ==================== 经停站 ====================

    @GetMapping("/train-station/list")
    @RequiresPermissions("b:train:list")
    public Result<List<TrainStationDTO>> stations(@RequestParam("trainId") long trainId) {
        return Result.ok(trainStationApi.listByTrain(trainId));
    }

    @PostMapping("/train-station/replace")
    @RequiresPermissions("b:train:edit")
    @OperLog(module = "车次管理", operation = "替换经停站")
    public Result<List<TrainStationDTO>> replaceStations(@RequestParam("trainId") long trainId,
                                                         @RequestBody List<TrainStationDTO> stations) {
        log.info("管理端替换经停站：operator={} trainId={} 共 {} 站", LoginContext.requireAdminId(),
                trainId, stations.size());
        return Result.ok(trainStationApi.replaceAll(trainId, stations));
    }

    // ==================== 车站 ====================

    @GetMapping("/station/list")
    public Result<List<StationDTO>> stationList() {
        return Result.ok(stationApi.listAll());
    }

    @PostMapping("/station")
    @RequiresPermissions("b:station:edit")
    @OperLog(module = "车站管理", operation = "新增车站")
    public Result<StationDTO> stationSave(@RequestBody StationDTO station) {
        log.info("管理端新增车站：operator={} code={} name={}", LoginContext.requireAdminId(),
                station.getCode(), station.getName());
        return Result.ok(stationApi.save(station));
    }

    @PutMapping("/station")
    @RequiresPermissions("b:station:edit")
    @OperLog(module = "车站管理", operation = "编辑车站")
    public Result<StationDTO> stationUpdate(@RequestBody StationDTO station) {
        log.info("管理端修改车站：operator={} id={} code={} name={}", LoginContext.requireAdminId(),
                station.getId(), station.getCode(), station.getName());
        return Result.ok(stationApi.update(station));
    }

    @DeleteMapping("/station/{id}")
    @RequiresPermissions("b:station:edit")
    @OperLog(module = "车站管理", operation = "删除车站")
    public Result<Void> stationDelete(@PathVariable long id) {
        log.info("管理端删除车站：operator={} id={}", LoginContext.requireAdminId(), id);
        stationApi.delete(id);
        return Result.ok();
    }
}

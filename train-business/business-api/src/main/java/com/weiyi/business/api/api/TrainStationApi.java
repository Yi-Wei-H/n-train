package com.weiyi.business.api.api;

import com.weiyi.business.api.dto.TrainStationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 车次经停站接口：按车次整体替换式保存（sort 定序，时刻与公里数供生成每日余票用）。
 */
@FeignClient(name = "train-business", contextId = "trainStationApi")
public interface TrainStationApi {

    @GetMapping("/tk/train-station/list")
    List<TrainStationDTO> listByTrain(@RequestParam("trainId") long trainId);

    /** 按车次代码查经停站时刻表（用户端展示，按经停顺序） */
    @GetMapping("/tk/train-station/list-by-code")
    List<TrainStationDTO> listByTrainCode(@RequestParam("trainCode") String trainCode);

    /** 整体替换某车次的经停站（先删后插，本地事务） */
    @PostMapping("/tk/train-station/replace")
    List<TrainStationDTO> replaceAll(@RequestParam("trainId") long trainId,
                                     @RequestBody List<TrainStationDTO> stations);
}

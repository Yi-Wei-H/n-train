package com.weiyi.business.api.api;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.business.api.dto.TrainDTO;
import com.weiyi.business.api.dto.TrainQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 车次管理接口（含车厢模板与模板座位生成）。
 */
@FeignClient(name = "train-business", contextId = "trainApi")
public interface TrainApi {

    @GetMapping("/tk/train/page")
    PageResult<TrainDTO> page(@SpringQueryMap TrainQuery query);

    @GetMapping("/tk/train/{id}")
    TrainDTO detail(@PathVariable("id") long id);

    @PostMapping("/tk/train/save")
    TrainDTO save(@RequestBody TrainDTO train);

    @PutMapping("/tk/train/update")
    TrainDTO update(@RequestBody TrainDTO train);

    @DeleteMapping("/tk/train/{id}")
    void delete(@PathVariable("id") long id);

    /** 按车厢模板生成模板座位（row_count × 列布局），返回生成数 */
    @PostMapping("/tk/train/{id}/generate-seats")
    int generateSeats(@PathVariable("id") long id);
}

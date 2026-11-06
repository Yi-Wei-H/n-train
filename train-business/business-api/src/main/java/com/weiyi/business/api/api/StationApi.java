package com.weiyi.business.api.api;

import com.weiyi.business.api.dto.StationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 车站管理接口。
 */
@FeignClient(name = "train-business", contextId = "stationApi")
public interface StationApi {

    @GetMapping("/tk/station/list")
    List<StationDTO> listAll();

    @PostMapping("/tk/station/save")
    StationDTO save(@RequestBody StationDTO station);

    @PutMapping("/tk/station/update")
    StationDTO update(@RequestBody StationDTO station);

    @DeleteMapping("/tk/station/{id}")
    void delete(@PathVariable("id") long id);
}

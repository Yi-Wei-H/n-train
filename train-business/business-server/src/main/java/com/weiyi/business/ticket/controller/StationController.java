package com.weiyi.business.ticket.controller;

import com.weiyi.business.api.api.StationApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.ticket.service.StationManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StationController implements StationApi {

    private final StationManageService stationManageService;

    @Override
    public List<StationDTO> listAll() {
        return stationManageService.listAll();
    }

    @Override
    public StationDTO save(StationDTO station) {
        return stationManageService.save(station);
    }

    @Override
    public StationDTO update(StationDTO station) {
        return stationManageService.update(station);
    }

    @Override
    public void delete(long id) {
        stationManageService.delete(id);
    }
}

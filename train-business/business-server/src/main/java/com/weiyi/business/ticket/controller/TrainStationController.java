package com.weiyi.business.ticket.controller;

import com.weiyi.business.api.api.TrainStationApi;
import com.weiyi.business.api.dto.TrainStationDTO;
import com.weiyi.business.ticket.service.TrainManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrainStationController implements TrainStationApi {

    private final TrainManageService trainManageService;

    @Override
    public List<TrainStationDTO> listByTrain(long trainId) {
        return trainManageService.listStations(trainId);
    }

    @Override
    public List<TrainStationDTO> listByTrainCode(String trainCode) {
        return trainManageService.listStationsByCode(trainCode);
    }

    @Override
    public List<TrainStationDTO> replaceAll(long trainId, List<TrainStationDTO> stations) {
        return trainManageService.replaceStations(trainId, stations);
    }
}

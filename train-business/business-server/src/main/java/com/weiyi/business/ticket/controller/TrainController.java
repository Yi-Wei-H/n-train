package com.weiyi.business.ticket.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.business.api.api.TrainApi;
import com.weiyi.business.api.dto.TrainDTO;
import com.weiyi.business.api.dto.TrainQuery;
import com.weiyi.business.ticket.service.TrainManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TrainController implements TrainApi {

    private final TrainManageService trainManageService;

    @Override
    public PageResult<TrainDTO> page(TrainQuery query) {
        return trainManageService.page(query);
    }

    @Override
    public TrainDTO detail(long id) {
        return trainManageService.detail(id);
    }

    @Override
    public TrainDTO save(TrainDTO train) {
        return trainManageService.save(train);
    }

    @Override
    public TrainDTO update(TrainDTO train) {
        return trainManageService.update(train);
    }

    @Override
    public void delete(long id) {
        trainManageService.delete(id);
    }

    @Override
    public int generateSeats(long id) {
        return trainManageService.generateSeats(id);
    }
}

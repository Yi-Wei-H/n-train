package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.business.api.dto.CarriageDTO;
import com.weiyi.business.api.dto.TrainDTO;
import com.weiyi.business.api.dto.TrainQuery;
import com.weiyi.business.api.dto.TrainStationDTO;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.entity.BTrain;
import com.weiyi.business.ticket.infra.entity.BTrainCarriage;
import com.weiyi.business.ticket.infra.entity.BTrainSeat;
import com.weiyi.business.ticket.infra.entity.BTrainStation;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainCarriageMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 车次/车厢/座位/经停站管理（admin 服务调用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainManageService {

    private final BTrainMapper trainMapper;
    private final BTrainCarriageMapper carriageMapper;
    private final BTrainSeatMapper seatMapper;
    private final BTrainStationMapper trainStationMapper;
    private final BStationMapper stationMapper;
    private final RouteService routeService;

    public PageResult<TrainDTO> page(TrainQuery query) {
        Page<BTrain> page = trainMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<BTrain>lambdaQuery()
                        .like(query.getCode() != null && !query.getCode().isBlank(),
                                BTrain::getCode, query.getCode())
                        .orderByAsc(BTrain::getCode));
        Map<Long, List<BTrainCarriage>> carriageByTrain = page.getRecords().isEmpty() ? Map.of()
                : carriageMapper.selectList(Wrappers.<BTrainCarriage>lambdaQuery()
                        .in(BTrainCarriage::getTrainId,
                                page.getRecords().stream().map(BTrain::getId).toList()))
                .stream().collect(Collectors.groupingBy(BTrainCarriage::getTrainId));
        return PageUtils.convert(page, t -> toDTO(t,
                carriageByTrain.getOrDefault(t.getId(), List.of()), null));
    }

    public TrainDTO detail(long id) {
        BTrain train = mustGet(id);
        List<BTrainCarriage> carriages = carriageMapper.selectList(Wrappers.<BTrainCarriage>lambdaQuery()
                .eq(BTrainCarriage::getTrainId, id).orderByAsc(BTrainCarriage::getCarriageNo));
        List<TrainStationDTO> stations = listStations(id);
        TrainDTO dto = toDTO(train, carriages, stations);
        return dto;
    }

    @Transactional
    public TrainDTO save(TrainDTO cmd) {
        assertCodeUnique(cmd.getCode(), null);
        BTrain train = new BTrain();
        train.setCode(cmd.getCode());
        train.setTrainType(cmd.getTrainType());
        trainMapper.insert(train);
        log.info("车次模板新增：id={} code={} 车厢 {} 节", train.getId(), train.getCode(),
                cmd.getCarriages() == null ? 0 : cmd.getCarriages().size());
        replaceCarriages(train.getId(), cmd.getCarriages());
        return detail(train.getId());
    }

    @Transactional
    public TrainDTO update(TrainDTO cmd) {
        BTrain train = mustGet(cmd.getId());
        assertCodeUnique(cmd.getCode(), train.getId());
        log.info("车次模板更新：id={} code {} → {} trainType {} → {}", train.getId(),
                train.getCode(), cmd.getCode(), train.getTrainType(), cmd.getTrainType());
        String oldCode = train.getCode();
        train.setCode(cmd.getCode());
        train.setTrainType(cmd.getTrainType());
        trainMapper.updateById(train);
        // 时刻表数据已调整：失效线路缓存，下次访问懒重建
        routeService.evict(oldCode);
        routeService.evict(cmd.getCode());
        if (cmd.getCarriages() != null) {
            replaceCarriages(train.getId(), cmd.getCarriages());
        }
        return detail(train.getId());
    }

    @Transactional
    public void delete(long id) {
        BTrain train = mustGet(id);
        trainMapper.deleteById(id);
        carriageMapper.delete(Wrappers.<BTrainCarriage>lambdaQuery().eq(BTrainCarriage::getTrainId, id));
        seatMapper.delete(Wrappers.<BTrainSeat>lambdaQuery().eq(BTrainSeat::getTrainId, id));
        trainStationMapper.delete(Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, id));
        routeService.evict(train.getCode());
        log.info("车次模板删除：id={} code={}（含车厢/座位/经停站）", id, train.getCode());
    }

    /** 按车厢模板生成模板座位（排数 × 列布局），先清后建 */
    @Transactional
    public int generateSeats(long trainId) {
        BTrain train = mustGet(trainId);
        Long stationCount = trainStationMapper.selectCount(
                Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, trainId));
        BizAssert.isTrue(stationCount > 0, ErrorCode.PARAM_ERROR, "请先维护经停站，再生成座位");
        List<BTrainCarriage> carriages = carriageMapper.selectList(Wrappers.<BTrainCarriage>lambdaQuery()
                .eq(BTrainCarriage::getTrainId, trainId));
        BizAssert.isTrue(!carriages.isEmpty(), ErrorCode.PARAM_ERROR, "请先维护车厢模板");

        seatMapper.delete(Wrappers.<BTrainSeat>lambdaQuery().eq(BTrainSeat::getTrainId, trainId));
        String emptySell = "0".repeat(stationCount.intValue());
        int generated = 0;
        for (BTrainCarriage carriage : carriages) {
            List<String> cols = SeatLayouts.colsOf(carriage.getSeatType());
            for (int row = 1; row <= carriage.getRowCount(); row++) {
                for (String col : cols) {
                    BTrainSeat seat = new BTrainSeat();
                    seat.setTrainId(trainId);
                    seat.setCarriageNo(carriage.getCarriageNo());
                    seat.setRowNo(row);
                    seat.setColNo(col);
                    seat.setSeatType(carriage.getSeatType());
                    seat.setSell(emptySell);
                    seatMapper.insert(seat);
                    generated++;
                }
            }
        }
        log.info("模板座位生成：trainId={} code={} 生成 {} 个座位", trainId, train.getCode(), generated);
        return generated;
    }

    // ==================== 经停站 ====================

    /** 按车次代码查经停站（用户端时刻表展示） */
    public List<TrainStationDTO> listStationsByCode(String trainCode) {
        BTrain train = trainMapper.selectOne(Wrappers.<BTrain>lambdaQuery().eq(BTrain::getCode, trainCode));
        BizAssert.notNull(train, ErrorCode.TRAIN_NOT_EXISTS);
        return listStations(train.getId());
    }

    public List<TrainStationDTO> listStations(long trainId) {
        List<BTrainStation> stops = trainStationMapper.selectList(Wrappers.<BTrainStation>lambdaQuery()
                .eq(BTrainStation::getTrainId, trainId).orderByAsc(BTrainStation::getSort));
        Map<Long, BStation> stationById = stops.isEmpty() ? Map.of()
                : stationMapper.selectBatchIds(stops.stream().map(BTrainStation::getStationId).toList())
                .stream().collect(Collectors.toMap(BStation::getId, Function.identity()));
        return stops.stream().map(stop -> {
            TrainStationDTO dto = new TrainStationDTO();
            dto.setId(stop.getId());
            dto.setTrainId(stop.getTrainId());
            BStation station = stationById.get(stop.getStationId());
            dto.setStationCode(station == null ? null : station.getCode());
            dto.setStationName(station == null ? null : station.getName());
            dto.setSort(stop.getSort());
            dto.setDepartureTime(stop.getDepartureTime());
            dto.setArrivalTime(stop.getArrivalTime());
            dto.setDistanceKm(stop.getDistanceKm());
            return dto;
        }).toList();
    }

    @Transactional
    public List<TrainStationDTO> replaceStations(long trainId, List<TrainStationDTO> stations) {
        BTrain train = mustGet(trainId);
        BizAssert.isTrue(stations != null && stations.size() >= 2, ErrorCode.PARAM_ERROR, "至少两个经停站");
        trainStationMapper.delete(Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, trainId));
        Map<String, BStation> stationByCode = stationMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(BStation::getCode, Function.identity()));
        int sort = 1;
        for (TrainStationDTO s : stations) {
            BStation station = stationByCode.get(s.getStationCode());
            BizAssert.notNull(station, ErrorCode.PARAM_ERROR, "车站不存在：" + s.getStationCode());
            BTrainStation stop = new BTrainStation();
            stop.setTrainId(trainId);
            stop.setStationId(station.getId());
            stop.setSort(sort++);
            stop.setDepartureTime(s.getDepartureTime());
            stop.setArrivalTime(s.getArrivalTime());
            stop.setDistanceKm(s.getDistanceKm());
            trainStationMapper.insert(stop);
        }
        // 经停站/时刻已调整：失效线路缓存，下次访问懒重建
        routeService.evict(train.getCode());
        log.info("经停站替换：trainId={} 共 {} 站", trainId, stations.size());
        return listStations(trainId);
    }

    // ==================== 内部 ====================

    private BTrain mustGet(long id) {
        BTrain train = trainMapper.selectById(id);
        BizAssert.notNull(train, ErrorCode.TRAIN_NOT_EXISTS);
        return train;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        Long count = trainMapper.selectCount(Wrappers.<BTrain>lambdaQuery()
                .eq(BTrain::getCode, code)
                .ne(excludeId != null, BTrain::getId, excludeId));
        BizAssert.isTrue(count == 0, ErrorCode.PARAM_ERROR, "车次号已存在：" + code);
    }

    private void replaceCarriages(long trainId, List<CarriageDTO> carriages) {
        carriageMapper.delete(Wrappers.<BTrainCarriage>lambdaQuery().eq(BTrainCarriage::getTrainId, trainId));
        if (carriages == null) {
            return;
        }
        for (CarriageDTO c : carriages) {
            BTrainCarriage entity = new BTrainCarriage();
            entity.setTrainId(trainId);
            entity.setCarriageNo(c.getCarriageNo());
            entity.setSeatType(c.getSeatType());
            entity.setSeatCount(c.getSeatCount() == null
                    ? (c.getRowCount() == null ? 0 : c.getRowCount() * SeatLayouts.colsOf(c.getSeatType()).size())
                    : c.getSeatCount());
            entity.setRowCount(c.getRowCount() == null ? 0 : c.getRowCount());
            carriageMapper.insert(entity);
        }
    }

    private TrainDTO toDTO(BTrain train, List<BTrainCarriage> carriages, List<TrainStationDTO> stations) {
        TrainDTO dto = new TrainDTO();
        dto.setId(train.getId());
        dto.setCode(train.getCode());
        dto.setTrainType(train.getTrainType());
        dto.setCarriages(carriages.stream().map(c -> {
            CarriageDTO cd = new CarriageDTO();
            cd.setId(c.getId());
            cd.setCarriageNo(c.getCarriageNo());
            cd.setSeatType(c.getSeatType());
            cd.setSeatCount(c.getSeatCount());
            cd.setRowCount(c.getRowCount());
            return cd;
        }).collect(Collectors.toList()));
        return dto;
    }
}

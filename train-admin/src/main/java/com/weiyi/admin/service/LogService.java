package com.weiyi.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.admin.dto.LogQuery;
import com.weiyi.admin.dto.LoginLogDTO;
import com.weiyi.admin.dto.OperLogDTO;
import com.weiyi.common.mq.event.LoginLogEvent;
import com.weiyi.common.mq.event.OperLogEvent;
import com.weiyi.admin.infra.entity.SysLoginLog;
import com.weiyi.admin.infra.entity.SysOperLog;
import com.weiyi.admin.infra.mapper.SysLoginLogMapper;
import com.weiyi.admin.infra.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录/操作日志（sys 库本地读写；会员端登录日志经 RocketMQ 异步汇入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    /** 管理端登录日志（本地直写）与会员端登录日志（MQ 消费）共用入口 */
    public void saveLoginLog(String userType, LoginLogEvent event) {
        SysLoginLog log = new SysLoginLog();
        log.setUserType(userType);
        log.setUserId(event.getUserId());
        log.setUsername(event.getUsername());
        log.setIp(event.getIp());
        log.setUserAgent(event.getUserAgent());
        log.setStatus(event.getStatus());
        log.setMsg(event.getMsg());
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
        // 局部变量 log（实体）遮蔽了 logger，必须写全限定名
        LogService.log.debug("登录日志落库：类型={} 账号={} 结果={} {}", userType,
                event.getUsername(), event.getStatus(), event.getMsg());
    }

    public PageResult<LoginLogDTO> pageLoginLogs(LogQuery query) {
        Page<SysLoginLog> page = loginLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<SysLoginLog>lambdaQuery()
                        .like(query.getUsername() != null && !query.getUsername().isBlank(),
                                SysLoginLog::getUsername, query.getUsername())
                        .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                        .orderByDesc(SysLoginLog::getLoginAt));
        log.info("登录日志分页查询：username={} status={} 第 {} 页 命中 {} 条/共 {} 条",
                query.getUsername(), query.getStatus(), query.getPageNum(),
                page.getRecords().size(), page.getTotal());
        return PageUtils.convert(page, entity -> {
            LoginLogDTO dto = new LoginLogDTO();
            dto.setId(entity.getId());
            dto.setUserType(entity.getUserType());
            dto.setUserId(entity.getUserId());
            dto.setUsername(entity.getUsername());
            dto.setIp(entity.getIp());
            dto.setStatus(entity.getStatus());
            dto.setMsg(entity.getMsg());
            dto.setLoginAt(entity.getLoginAt());
            return dto;
        });
    }

    public void saveOperLog(OperLogEvent event) {
        SysOperLog log = new SysOperLog();
        log.setUserId(event.getUserId());
        log.setUsername(event.getUsername());
        log.setModule(event.getModule());
        log.setOperation(event.getOperation());
        log.setMethod(event.getMethod());
        log.setParams(event.getParams());
        log.setResultCode(event.getResultCode());
        log.setIp(event.getIp());
        log.setCostMs(event.getCostMs());
        log.setOperAt(LocalDateTime.now());
        operLogMapper.insert(log);
        // 局部变量 log（实体）遮蔽了 logger，必须写全限定名
        LogService.log.info("操作日志落库：账号={} 模块={} 操作={} 结果码={} 耗时={}ms",
                event.getUsername(), event.getModule(), event.getOperation(),
                event.getResultCode(), event.getCostMs());
    }

    public PageResult<OperLogDTO> pageOperLogs(LogQuery query) {
        Page<SysOperLog> page = operLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<SysOperLog>lambdaQuery()
                        .like(query.getUsername() != null && !query.getUsername().isBlank(),
                                SysOperLog::getUsername, query.getUsername())
                        .orderByDesc(SysOperLog::getOperAt));
        log.info("操作日志分页查询：username={} 第 {} 页 命中 {} 条/共 {} 条",
                query.getUsername(), query.getPageNum(), page.getRecords().size(), page.getTotal());
        return PageUtils.convert(page, entity -> {
            OperLogDTO dto = new OperLogDTO();
            dto.setId(entity.getId());
            dto.setUserId(entity.getUserId());
            dto.setUsername(entity.getUsername());
            dto.setModule(entity.getModule());
            dto.setOperation(entity.getOperation());
            dto.setMethod(entity.getMethod());
            dto.setParams(entity.getParams());
            dto.setResultCode(entity.getResultCode());
            dto.setIp(entity.getIp());
            dto.setCostMs(entity.getCostMs());
            dto.setOperAt(entity.getOperAt());
            return dto;
        });
    }
}

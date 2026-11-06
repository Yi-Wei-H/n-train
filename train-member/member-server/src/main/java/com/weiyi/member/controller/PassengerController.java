package com.weiyi.member.controller;

import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.member.service.PassengerService;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.member.dto.PassengerDTO;
import com.weiyi.member.dto.PassengerSaveCmd;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 乘车人管理（memberId 一律取登录态，防越权）。
 */
@RestController
@RequestMapping("/passenger")
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @GetMapping("/list")
    public Result<List<PassengerDTO>> list() {
        return Result.ok(passengerService.listByMember(requireMember()));
    }

    @PostMapping
    public Result<PassengerDTO> save(@RequestBody @Valid PassengerSaveCmd cmd) {
        return Result.ok(passengerService.save(requireMember(), cmd));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        passengerService.delete(id, requireMember());
        return Result.ok();
    }

    private long requireMember() {
        Long memberId = LoginContext.requireMemberId();
        if (memberId == null) {
            throw new com.weiyi.common.core.exception.BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }
}

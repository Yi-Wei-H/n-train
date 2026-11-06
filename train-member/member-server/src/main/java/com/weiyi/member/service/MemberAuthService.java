package com.weiyi.member.service;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.PasswordUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.MqSender;
import com.weiyi.common.mq.event.LoginLogEvent;
import com.weiyi.common.redis.service.LoginFailGuard;
import com.weiyi.common.redis.service.RsaKeyService;
import com.weiyi.common.redis.util.RequestIdentityUtil;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.common.security.util.JwtUtil;
import com.weiyi.member.api.dto.MemberAuthDTO;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.api.dto.RegisterDTO;
import com.weiyi.member.dto.LoginResult;
import com.weiyi.member.dto.RegisterCmd;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会员账号服务：注册（图形验证码 + RSA 密文密码 + 身份证实名）、
 * 登录（密码/短信验证码双模式，共享失败锁定）、登录日志。
 * 登录日志经 RocketMQ 异步投递（管理域消费落 sys_login_log）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthService {

    private final MemberUserService memberUserService;
    private final MqSender mqSender;
    private final CaptchaService captchaService;
    private final SmsCodeService smsCodeService;
    private final JwtUtil jwtUtil;
    private final RsaKeyService rsaKeyService;
    private final LoginFailGuard loginFailGuard;

    public MemberDTO register(RegisterCmd cmd, HttpServletRequest request) {
        captchaService.verifyAndConsume(cmd.captchaKey(), cmd.captchaCode());
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone(cmd.phone());
        dto.setIdCardNo(cmd.idCardNo());
        dto.setPassword(rsaKeyService.decrypt(cmd.keyId(), cmd.cipher()));
        MemberDTO member = memberUserService.register(dto);
        saveLoginLog(member.getId(), member.getPhone(), 1, "注册成功", request);
        log.info("会员注册成功：memberId={} phone={} ip={}", member.getId(), member.getPhone(), RequestIdentityUtil.currentIp());
        return member;
    }

    /** 发送短信验证码：先过图形验证码（防脚本刷短信），再走 60s 冷却 + 已注册校验；返回验证码供模拟回显 */
    public String sendCode(String captchaKey, String captchaCode, String phone) {
        captchaService.verifyAndConsume(captchaKey, captchaCode);
        String code = smsCodeService.sendCode(phone);
        log.info("短信验证码已发送：phone={}", phone);
        return code;
    }

    /**
     * 双模式登录：cipher 非空走密码（RSA 解密 + BCrypt），
     * 否则走短信验证码（一次一用）；两分支共享失败锁定（防验证码爆破）与成功清零。
     */
    public LoginResult login(String phone, String keyId, String cipher, String smsCode,
                             HttpServletRequest request) {
        loginFailGuard.checkLocked(Constants.USER_TYPE_MEMBER, phone);
        MemberAuthDTO auth = memberUserService.getAuthByPhone(phone);
        String loginMode = (cipher != null && !cipher.isBlank()) ? "密码" : "短信验证码";
        if (auth == null) {
            loginFailGuard.recordFail(Constants.USER_TYPE_MEMBER, phone);
            saveLoginLog(0, phone, 0, "账号不存在", request);
            log.warn("会员登录失败：phone={} 模式={} 原因=账号不存在 ip={}", phone, loginMode, RequestIdentityUtil.currentIp());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (cipher != null && !cipher.isBlank()) {
            String password = rsaKeyService.decrypt(keyId, cipher);
            if (!PasswordUtil.matches(password, auth.getPassword())) {
                loginFailGuard.recordFail(Constants.USER_TYPE_MEMBER, phone);
                log.warn("会员登录失败：memberId={} phone={} 模式=密码 原因=密码错误 ip={}",
                        auth.getId(), phone, RequestIdentityUtil.currentIp());
                throw new BusinessException(ErrorCode.PASSWORD_ERROR);
            }
        } else {
            try {
                smsCodeService.verifyAndConsume(phone, smsCode);
            } catch (BusinessException e) {
                loginFailGuard.recordFail(Constants.USER_TYPE_MEMBER, phone);
                log.warn("会员登录失败：memberId={} phone={} 模式=短信验证码 原因={} ip={}",
                        auth.getId(), phone, e.getMessage(), RequestIdentityUtil.currentIp());
                throw e;
            }
        }
        BizAssert.isTrue(auth.getStatus() != null && auth.getStatus() == 1, ErrorCode.ACCOUNT_DISABLED);
        loginFailGuard.clear(Constants.USER_TYPE_MEMBER, phone);
        MemberDTO member = memberUserService.getById(auth.getId());
        String token = jwtUtil.create(auth.getId(), Constants.USER_TYPE_MEMBER, phone);
        saveLoginLog(auth.getId(), phone, 1, "登录成功", request);
        log.info("会员登录成功：memberId={} phone={} 模式={} ip={}",
                auth.getId(), phone, loginMode, RequestIdentityUtil.currentIp());
        return new LoginResult(token, member);
    }

    public MemberDTO me() {
        return memberUserService.getById(LoginContext.requireMemberId());
    }

    /** 登录日志 → MQ（管理域消费落 sys_login_log；fire-and-forget，失败不影响登录） */
    private void saveLoginLog(long userId, String username, int status, String msg,
                              HttpServletRequest request) {
        try {
            LoginLogEvent event = new LoginLogEvent();
            event.setUserId(userId);
            event.setUsername(username);
            event.setIp(RequestIdentityUtil.currentIp());
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setStatus(status);
            event.setMsg(msg);
            mqSender.sendQuietly(MqConst.TOPIC_LOG_EVENT, MqConst.TAG_LOG_LOGIN, username, event);
        } catch (Exception e) {
            log.warn("登录日志投递失败: {}", e.getMessage());
        }
    }
}

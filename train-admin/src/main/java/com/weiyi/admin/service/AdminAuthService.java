package com.weiyi.admin.service;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.PasswordUtil;
import com.weiyi.common.redis.service.LoginFailGuard;
import com.weiyi.common.redis.service.RsaKeyService;
import com.weiyi.common.redis.util.RequestIdentityUtil;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.common.security.util.JwtUtil;
import com.weiyi.common.mq.event.LoginLogEvent;
import com.weiyi.admin.dto.AdminAuthDTO;
import com.weiyi.admin.dto.AdminUserDTO;
import com.weiyi.admin.dto.LoginCmd;
import com.weiyi.admin.dto.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理员登录：锁定检查 → RSA 解密 → 验 BCrypt → 签发 JWT（userType=A，超管带 super 声明）→ 登录日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final SysUserService sysUserService;
    private final LogService logService;
    private final JwtUtil jwtUtil;
    private final RsaKeyService rsaKeyService;
    private final LoginFailGuard loginFailGuard;

    public LoginResult login(LoginCmd cmd, HttpServletRequest request) {
        loginFailGuard.checkLocked(Constants.USER_TYPE_ADMIN, cmd.username());
        String password = rsaKeyService.decrypt(cmd.keyId(), cmd.cipher());
        AdminAuthDTO auth = sysUserService.getAuthByUsername(cmd.username());
        if (auth == null) {
            loginFailGuard.recordFail(Constants.USER_TYPE_ADMIN, cmd.username());
            saveLoginLog(0, cmd.username(), "账号不存在", request);
            log.warn("管理员登录失败：username={} 原因=账号不存在 ip={}", cmd.username(), RequestIdentityUtil.currentIp());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (!PasswordUtil.matches(password, auth.getPassword())) {
            loginFailGuard.recordFail(Constants.USER_TYPE_ADMIN, cmd.username());
            log.warn("管理员登录失败：adminId={} username={} 原因=密码错误 ip={}",
                    auth.getId(), cmd.username(), RequestIdentityUtil.currentIp());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        BizAssert.isTrue(auth.getStatus() != null && auth.getStatus() == 1, ErrorCode.ACCOUNT_DISABLED);
        loginFailGuard.clear(Constants.USER_TYPE_ADMIN, cmd.username());
        boolean superAdmin = Constants.ADMIN_TYPE_SUPER.equals(auth.getUserType());
        String token = jwtUtil.create(auth.getId(), Constants.USER_TYPE_ADMIN, auth.getUsername(), superAdmin);
        sysUserService.updateLastLogin(auth.getId());
        saveLoginLog(auth.getId(), auth.getUsername(), "登录成功", request);
        log.info("管理员登录成功：adminId={} username={} 超管={} ip={}",
                auth.getId(), auth.getUsername(), superAdmin, RequestIdentityUtil.currentIp());
        return new LoginResult(token, auth.getNickname(), superAdmin);
    }

    public AdminUserDTO me() {
        return sysUserService.detail(LoginContext.requireAdminId());
    }

    private void saveLoginLog(long userId, String username, String msg, HttpServletRequest request) {
        try {
            LoginLogEvent event = new LoginLogEvent();
            event.setUserId(userId);
            event.setUsername(username);
            event.setIp(RequestIdentityUtil.currentIp());
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setStatus(msg.contains("成功") ? 1 : 0);
            event.setMsg(msg);
            logService.saveLoginLog(Constants.USER_TYPE_ADMIN, event);
        } catch (Exception e) {
            log.warn("登录日志写入失败: {}", e.getMessage());
        }
    }
}

package com.weiyi.admin.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.util.PasswordUtil;
import com.weiyi.admin.infra.entity.SysUser;
import com.weiyi.admin.infra.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 初始超管：首次启动（sys_user 为空）时创建 admin 账号，
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SuperAdminInitRunner implements ApplicationRunner {

    private final SysUserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(Wrappers.emptyWrapper());
        if (count > 0) {
            return;
        }
        log.info("正在创建超管账号...");
        String password = System.getenv("SUPER_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("首次初始化需要环境变量 SUPER_ADMIN_PASSWORD");
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(PasswordUtil.hash(password));
        admin.setNickname("超级管理员");
        admin.setDeptId(1L);
        admin.setUserType(Constants.ADMIN_TYPE_SUPER);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已创建初始超管 admin，请尽快修改");
    }
}

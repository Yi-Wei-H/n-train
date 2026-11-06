package com.weiyi.admin.shiro;

import com.weiyi.common.security.auth.PermissionResolver;
import com.weiyi.admin.service.SysUserService;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shiro 无状态配置（session 全关，权限缓存统一在 AdminPermService）。
 * 注意：application.yml 已关闭 shiro starter 自动装配（shiro.enabled=false），全部手动装配。
 */
@Configuration
public class ShiroConfig {

    @Bean
    public AdminRealm adminRealm(SysUserService sysUserService, PermissionResolver permissionResolver) {
        return new AdminRealm(sysUserService, permissionResolver);
    }

    @Bean
    public SecurityManager securityManager(AdminRealm adminRealm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager(adminRealm);

        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator evaluator = new DefaultSessionStorageEvaluator();
        evaluator.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(evaluator);
        securityManager.setSubjectDAO(subjectDAO);

        DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
        sessionManager.setSessionValidationSchedulerEnabled(false);
        securityManager.setSessionManager(sessionManager);

        org.apache.shiro.SecurityUtils.setSecurityManager(securityManager);
        return securityManager;
    }

    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager,
                                                         ShiroAnonProperties anonProperties) {
        ShiroFilterFactoryBean bean = new ShiroFilterFactoryBean();
        bean.setSecurityManager(securityManager);
        bean.getFilters().put("statelessAuth", new StatelessAuthFilter());
        Map<String, String> chain = new LinkedHashMap<>();
        // 免鉴权路径可配置（默认：登录/公钥/文档），其余全走无状态鉴权
        for (String anonPath : anonProperties.getAnonPaths()) {
            chain.put(anonPath, "anon");
        }
        chain.put("/**", "statelessAuth");
        bean.setFilterChainDefinitionMap(chain);
        return bean;
    }

    /** 开启 @RequiresPermissions 注解校验 */
    @Bean
    public static DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }
}

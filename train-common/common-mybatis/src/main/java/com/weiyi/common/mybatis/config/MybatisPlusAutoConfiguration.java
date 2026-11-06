package com.weiyi.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 插件配置（持库服务统一引用，agent 也复用）：
 * 分页（防全表，方言可配）+ 乐观锁 + 防全表更新删除 + created_at/updated_at 自动填充。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusAutoConfiguration {

    /** 分页方言：默认 MYSQL（四个 MySQL 服务）；agent 域在配置中心设 POSTGRE_SQL */
    @Value("${ntrain.mybatis.db-type:MYSQL}")
    private DbType dbType;

    /**
     * 实现 MyBatis 原生的 Interceptor 接口。
     * 原生 MyBatis 每个插件单独注册不同，MP 要求所有插件都塞进这一个 Bean 里统一调度。
     * MP的拦截器：管理SQL行为；内部有一串拦截器interceptors，
     * SQL 执行时按添加顺序依次过一遍每个内部拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        /* 分页拦截器：将页面大小限制到200，防止恶意大分页（方言随库配置） */
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(dbType);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        /*
        * 乐观锁拦截器：让实体上的 @Version 字段生效：执行 updateById 时自动把 UPDATE 改写成
        * UPDATE m_wallet SET balance=..., version=version+1 WHERE id=? AND version=?
        */
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        /* 防全表更新删除拦截器。拦下不带 where 条件的 UPDATE/DELETE(直接抛异常) */
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    /**
     * 自动填充字段：这里这里主要是自动填充创建/更新时间
     * MP 的内置方法(insert / updateById / update(entity, wrapper))执行时，
     * 会检查实体的字段上有没有 fill 注解，有就回调对应方法。
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}

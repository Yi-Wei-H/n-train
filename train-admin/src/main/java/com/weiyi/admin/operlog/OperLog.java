package com.weiyi.admin.operlog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要记录操作日志的管理端接口（只标写操作，查询不打）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 业务模块名，如"管理员管理" */
    String module();

    /** 操作动作名，如"新增管理员" */
    String operation();
}

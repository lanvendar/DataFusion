package com.datafusion.datasource.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 单 sql 获取注解(渲染出单条sql执行).
 *
 * @author lanvendar
 * @version V1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SqlGet {
    /**
     * SQL 定位路径字符串，是 sqlKey() 的别名.
     * 允许使用 @SqlGet("namespace.key") 的简洁形式
     *
     * @return sql模板路径字符串
     */
    @AliasFor("sqlKey") String value() default "";
    
    /**
     * SQL 定位路径字符串.
     *
     * @return sql模板路径字符串
     */
    @AliasFor("value") String sqlKey() default "";
    
    /**
     * 是否为批量模式.
     * true:  表示批量执行，对应 JFinalSqlBuilder.renderSqlBatch()
     * false: (默认)表示单条执行，对应 JFinalSqlBuilder.renderSql()
     *
     * @return boolean
     */
    boolean isBatch() default false;
}

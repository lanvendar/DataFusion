package com.datafusion.datasource.spring;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源扫描注解.
 *
 * @author lanvendar
 * @version V1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AutoSqlScanImportBeanRegistrar.class)
public @interface SqlScan {
    
    /**
     * 扫描具体包名.
     *
     * @return 数组
     */
    @AliasFor("value") String[] basePackages() default {};
    
    /**
     * 扫描基础包名.
     *
     * @return 数组
     */
    @AliasFor("basePackages") String[] value() default {};
}

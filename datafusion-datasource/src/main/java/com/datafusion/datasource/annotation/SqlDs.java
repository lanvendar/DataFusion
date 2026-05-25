package com.datafusion.datasource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多数据源注解.
 * 指定要使用的数据源
 *
 * @author lanvendar
 * @version V1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface SqlDs {
    /**
     * 数据源Id.
     *
     * @return 数据源Id
     */
    String id() default "";
    
    /**
     * 数据源类型.
     *
     * @return 数据源类型
     */
    String dataBaseType() default "";
}
package com.datafusion.datasource.spring;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * sql代理注解.
 *
 * @author lanvendar
 * @version V1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface SqlRepository {
    
    /**
     * 执行sql接口代理.
     * <p>自动检测到组件的情况下将其转换为 spring bean</p>
     *
     * @return String
     */
    @AliasFor(annotation = Component.class) String value() default "";
}
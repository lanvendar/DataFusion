package com.datafusion.datasource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法参数绑定到SQL模板中的具名参数注解,用于单条sql的参数.
 * <p>
 * 此注解用于将一个Java方法的参数与SQL模板中通过 {@code #para(...)} 或 {@code #p(...)}
 * 指令引用的命名参数进行映射,它主要用于非批量的、单次执行的SQL语句.
 * </p>
 * <p>与 {@link SqlParams} 的区别:</p>
 * <ul>
 *   <li>{@code @SqlParam} 用于将**单个**方法参数映射到**单个**SQL命名参数，通常一个方法中会使用多个。</li>
 *   <li>{@link SqlParams} 用于标记一个方法参数（通常是 {@code Collection} 或数组）作为**批量数据**的来源，
 *       用于批量执行或 {@code IN} 子句等场景，它本身不进行名称映射。</li>
 * </ul>
 *
 * @author lanvendar, 2025/8/26
 * @version V1.0.0
 * @since 2025/8/26
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SqlParam {
    
    /**
     * sql参数名称映射.
     *
     * @return sql参数名称映射
     */
    String value() default "";
}
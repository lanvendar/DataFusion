package com.datafusion.datasource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法参数作为批量操作的数据源.
 *
 * <p>
 * 此注解用于两种场景:
 * <ol>
 *   <li><b>批量执行多条SQL:</b> 与 {@code @SqlGet(isBatch = true)} 配合使用，
 *       注解的参数应为 {@code Collection} 或数组，其中每个元素对应一次SQL执行的参数
 *   </li>
 *   <li><b>单条SQL处理多条记录:</b> 与 {@code @SqlGet(isBatch = false)} 配合使用，
 *       常用于 {@code IN} 子句或多行 {@code INSERT}。
 *       注解的参数（通常是集合）会在SQL模板中通过一个内部约定的名称 (e.g., _symbol_insert_rows_) 进行访问
 *   </li>
 * </ol>
 * </p>
 *
 * @author lanvendar
 * @version V1.0.0, 2025/8/26
 * @since 2025/8/26
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SqlParams {
}
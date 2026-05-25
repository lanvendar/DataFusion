package com.datafusion.datasource.resultset;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;

/**
 * 结果集类型处理.
 * @param <R> 源 Result 类型
 * @author lanvendar
 * @version 1.0.0, 2025/7/8
 * @since 2025/7/8
 */
@AllArgsConstructor
@Data
public class ResultSetMapping<R> {
    /**
     * 数据库列名.
     */
    private String columnLabel;
    
    /**
     * 属性的Setter方法.
     */
    private Method setterMethod;
    
    /**
     * 用于处理该属性类型的处理器.
     */
    private TypeHandler<R, ?> typeHandler;
}

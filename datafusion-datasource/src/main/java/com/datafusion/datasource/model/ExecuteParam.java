package com.datafusion.datasource.model;

import com.datafusion.common.template.SqlParamRender;
import lombok.Data;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 执行参数对象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
@Data
public class ExecuteParam {
    /**
     * 数据源ID.
     */
    private String dsId;
    
    /**
     * sql模板路径.
     */
    private String sqlKey;
    
    /**
     * 是否批量执行.
     */
    private boolean isBatch;
    
    /**
     * 渲染后单 SQL 参数对象.
     */
    private SqlParamRender render;
    
    /**
     * 渲染后多 SQL 参数对象.
     */
    private List<SqlParamRender> renders;
    
    /**
     * 参数对象.
     */
    private Map<String, Object> param;
    
    /**
     * 参数对象.
     */
    private List<Map<String, Object>> params;
    
    /**
     * 返回值类型.
     */
    private Type returnType;
    
}

package com.datafusion.common.template;

import com.datafusion.common.enums.CommandType;
import com.datafusion.common.enums.JdbcExecutionType;
import com.datafusion.common.enums.RenderType;
import com.jfinal.template.Template;
import lombok.Data;

import java.io.Serializable;

/**
 * SqlTemplate 模板定义类对象.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/04/11
 * @since 2022/04/11
 */
@Data
public class SqlTemplate implements Serializable {
    
    private static final long serialVersionUID = -656208232379029277L;
    
    /**
     * sql指令.
     */
    private CommandType commandType;
    
    /**
     * 编译类型.
     */
    private JdbcExecutionType executionType;
    
    /**
     * 渲染类型.
     */
    private RenderType renderType;
    
    /**
     * sql 模板.
     */
    private Template template;
}

package com.datafusion.plugin.kafka.json.expression;

import java.io.Serializable;

/**
 * JMESPath 表达式和默认值定义.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExpressionSpec implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * JMESPath 表达式.
     */
    public String path;

    /**
     * 默认值.
     */
    public Object defaultValue;

    /**
     * JSON 顶层类型.
     */
    public String jsonType = JsonType.ANY.name();
}

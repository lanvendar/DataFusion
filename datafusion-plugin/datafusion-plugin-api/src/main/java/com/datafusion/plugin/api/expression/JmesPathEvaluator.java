package com.datafusion.plugin.api.expression;

import io.burt.jmespath.Expression;
import io.burt.jmespath.jcf.JcfRuntime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMESPath 表达式求值器.
 *
 * <p>
 * 支持表达式的编译缓存,提高重复执行的性能.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class JmesPathEvaluator {
    
    /** JMESPath 运行时实例. */
    private final JcfRuntime runtime = new JcfRuntime();
    
    /** 表达式编译缓存. */
    private final Map<String, Expression<Object>> cache = new ConcurrentHashMap<>();

    /**
     * 执行 JMESPath 表达式查询.
     *
     * @param input 输入数据
     * @param expression JMESPath 表达式
     * @return 查询结果
     */
    public Object search(Object input, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        return cache.computeIfAbsent(expression, runtime::compile).search(input);
    }

    /**
     * 判断表达式结果是否为真值.
     *
     * @param input 输入数据
     * @param expression JMESPath 表达式
     * @return true 表示真值
     */
    public boolean isTruthy(Object input, String expression) {
        Object value = search(input, expression);
        return runtime.isTruthy(value);
    }
}

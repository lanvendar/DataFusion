package com.datafusion.plugin.kafka.json.expression;

import io.burt.jmespath.Expression;
import io.burt.jmespath.jcf.JcfRuntime;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMESPath 表达式求值器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class JmesPathEvaluator implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * JCF 运行时.
     */
    private transient JcfRuntime runtime;

    /**
     * 编译缓存.
     */
    private transient Map<String, Expression<Object>> cache;

    /**
     * 执行表达式.
     *
     * @param input 输入对象
     * @param expression 表达式
     * @return 求值结果
     */
    public Object search(Object input, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        return cache().computeIfAbsent(expression, runtime()::compile).search(input);
    }

    private JcfRuntime runtime() {
        if (runtime == null) {
            runtime = new JcfRuntime();
        }
        return runtime;
    }

    private Map<String, Expression<Object>> cache() {
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
        }
        return cache;
    }
}

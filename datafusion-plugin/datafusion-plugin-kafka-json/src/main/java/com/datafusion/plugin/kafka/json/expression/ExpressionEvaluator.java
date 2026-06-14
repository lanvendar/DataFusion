package com.datafusion.plugin.kafka.json.expression;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

/**
 * 表达式执行器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExpressionEvaluator {

    /**
     * JMESPath 求值器.
     */
    private final JmesPathEvaluator evaluator = new JmesPathEvaluator();

    /**
     * 解析表达式值.
     *
     * @param input 输入上下文
     * @param spec 表达式配置
     * @param name 表达式名称
     * @return 解析结果
     */
    public Object evaluate(Object input, ExpressionSpec spec, String name) {
        if (spec == null) {
            return null;
        }
        Object value = TextUtils.isBlank(spec.path) ? null : evaluator.search(input, spec.path);
        if (isEmpty(value)) {
            value = spec.defaultValue;
        }
        JsonType type = JsonType.parse(spec.jsonType);
        if (!type.matches(value)) {
            throw new KafkaJsonPaimonException("Expression result type mismatch: " + name + ", expected=" + type);
        }
        return value;
    }

    /**
     * 判断表达式结果是否为空.
     *
     * @param value 表达式结果
     * @return true 表示为空
     */
    public static boolean isEmpty(Object value) {
        return value == null || value instanceof String text && TextUtils.isBlank(text);
    }
}

package com.datafusion.plugin.api.mapping;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ResponseConfig;
import com.datafusion.plugin.api.core.ApiExtractContext;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.expression.JmesPathEvaluator;
import com.datafusion.plugin.api.template.TemplateResolver;
import com.datafusion.plugin.api.util.JsonUtils;
import com.datafusion.plugin.api.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录映射器,将 API 响应 JSON 转换为结构化记录.
 *
 * <p>
 * 支持 OBJECT 和 ARRAY 两种记录模式,使用 JMESPath 表达式提取字段.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class RecordMapper {
    
    /**
     * JMESPath 表达式求值器.
     */
    private final JmesPathEvaluator evaluator;
    
    /**
     * 模板解析器.
     */
    private final TemplateResolver templateResolver;

    /**
     * 构造记录映射器.
     *
     * @param evaluator JMESPath 求值器
     * @param templateResolver 模板解析器
     */
    public RecordMapper(JmesPathEvaluator evaluator, TemplateResolver templateResolver) {
        this.evaluator = evaluator;
        this.templateResolver = templateResolver;
    }

    /**
     * 将 JSON 响应映射为记录列表.
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 记录列表
     */
    public List<Record> map(ResponseConfig response, Object json, ApiExtractContext context) {
        if (response == null || response.fields == null || response.fields.isEmpty()) {
            return List.of();
        }
        String mode = TextUtils.upper(response.recordMode, "OBJECT");
        if ("OBJECT".equals(mode)) {
            return List.of(mapObject(response, json, context));
        }
        if ("ARRAY".equals(mode)) {
            return mapArray(response, json, context);
        }
        throw new ApiExtractException("Unsupported recordMode: " + response.recordMode);
    }

    /**
     * 映射单条对象记录.
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 单条记录
     */
    private Record mapObject(ResponseConfig response, Object json, ApiExtractContext context) {
        Record record = new Record();
        for (FieldConfig field : response.fields) {
            Object value = resolveField(field, json, context);
            assertNullable(field, value);
            record.put(field.name, normalizeValue(value));
        }
        return record;
    }

    /**
     * 映射数组记录,根据 isKey 字段决定记录条数.
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 记录列表
     */
    private List<Record> mapArray(ResponseConfig response, Object json, ApiExtractContext context) {
        FieldConfig keyField = response.fields.stream().filter(field -> field.isKey).findFirst()
                .orElseThrow(() -> new ApiExtractException("ARRAY recordMode requires isKey field"));
        Object keyValue = resolveField(keyField, json, context);
        if (!(keyValue instanceof List<?> keyValues)) {
            throw new ApiExtractException("ARRAY isKey field must return array: " + keyField.name);
        }
        List<Record> records = new ArrayList<>(keyValues.size());
        for (int i = 0; i < keyValues.size(); i++) {
            Object value = keyValues.get(i);
            if (isEmpty(value)) {
                throw new ApiExtractException("ARRAY isKey field contains empty value: " + keyField.name + "[" + i + "]");
            }
            Record record = new Record();
            record.put(keyField.name, normalizeValue(value));
            records.add(record);
        }
        for (FieldConfig field : response.fields) {
            if (field == keyField) {
                continue;
            }
            Object fieldValue = resolveField(field, json, context);
            if (fieldValue instanceof List<?> values) {
                if (values.size() != keyValues.size()) {
                    throw new ApiExtractException("ARRAY field length mismatch: " + field.name);
                }
                for (int i = 0; i < values.size(); i++) {
                    Object item = values.get(i);
                    assertNullable(field, item);
                    records.get(i).put(field.name, normalizeValue(item));
                }
            } else {
                assertNullable(field, fieldValue);
                Object normalized = normalizeValue(fieldValue);
                for (Record record : records) {
                    record.put(field.name, normalized);
                }
            }
        }
        return records;
    }

    /**
     * 解析字段值,支持固定值和表达式两种方式.
     *
     * @param field 字段配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 字段值
     */
    private Object resolveField(FieldConfig field, Object json, ApiExtractContext context) {
        if (field.value != null) {
            return templateResolver.resolveObject(field.value, context);
        }
        return evaluator.search(json, field.expression);
    }

    /**
     * 断言字段非空约束.
     *
     * @param field 字段配置
     * @param value 字段值
     * @throws ApiExtractException 必填字段为空时抛出
     */
    private void assertNullable(FieldConfig field, Object value) {
        if (!field.nullable && isEmpty(value)) {
            throw new ApiExtractException("Required field is empty: " + field.name);
        }
    }

    /**
     * 判断值是否为空.
     *
     * @param value 待判断的值
     * @return true 表示空值
     */
    private boolean isEmpty(Object value) {
        return value == null || value instanceof String text && TextUtils.isBlank(text);
    }

    /**
     * 标准化值类型,将复杂对象序列化为 JSON 字符串.
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    private Object normalizeValue(Object value) {
        if (value instanceof java.util.Map || value instanceof Iterable) {
            return JsonUtils.write(value);
        }
        return value;
    }
}

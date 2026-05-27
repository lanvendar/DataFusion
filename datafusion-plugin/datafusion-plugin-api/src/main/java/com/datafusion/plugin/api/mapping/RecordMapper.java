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
     * <p>
     * 优先使用逐条提取策略: 先通过 isKey 表达式提取数组元素,
     * 然后对每个元素逐一提取字段值,避免 JMESPath 投影过滤 null 导致长度不一致.
     * 如果 isKey 表达式无法拆分为数组路径和字段路径,则回退到旧的全量投影策略.
     * </p>
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 记录列表
     */
    private List<Record> mapArray(ResponseConfig response, Object json, ApiExtractContext context) {
        FieldConfig keyField = response.fields.stream().filter(field -> field.isKey).findFirst()
                .orElseThrow(() -> new ApiExtractException("ARRAY recordMode requires isKey field"));

        // 尝试逐条提取: 从 key 表达式中推导出数组路径,逐条解析字段
        String[] keyParts = splitArrayProjection(keyField.expression);
        if (keyParts != null) {
            return mapArrayByElement(response, json, context, keyParts);
        }

        // 回退策略: 全量投影提取
        return mapArrayByProjection(response, json, context, keyField);
    }

    /**
     * 逐条提取策略: 先提取数组元素,再对每个元素逐一解析字段.
     *
     * <p>
     * 从 isKey 表达式推导出数组路径(如 "Data[]")和字段路径(如 "Today"),
     * 先提取数组,然后对每个元素逐一执行 JMESPath 查询提取字段值.
     * 这种方式不会因 JMESPath 投影过滤 null 而导致长度不一致.
     * </p>
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @param keyParts 数组路径和 isKey 字段路径
     * @return 记录列表
     */
    private List<Record> mapArrayByElement(ResponseConfig response, Object json, ApiExtractContext context,
            String[] keyParts) {
        String arrayPath = keyParts[0];
        String keyFieldPath = keyParts[1];
        FieldConfig keyField = response.fields.stream().filter(field -> field.isKey).findFirst().orElseThrow();

        Object arrayResult = evaluator.search(json, arrayPath);
        if (!(arrayResult instanceof List<?> elements)) {
            throw new ApiExtractException("ARRAY expression must resolve to an array: " + arrayPath);
        }
        List<Record> records = new ArrayList<>(elements.size());
        for (Object element : elements) {
            Object keyValue = evaluator.search(element, keyFieldPath);
            if (isEmpty(keyValue)) {
                throw new ApiExtractException("ARRAY isKey field contains empty value: " + keyField.name);
            }
            Record record = new Record();
            record.put(keyField.name, normalizeValue(keyValue));
            for (FieldConfig field : response.fields) {
                if (field.isKey) {
                    continue;
                }
                Object fieldValue = resolveFieldOnElement(field, element, context);
                assertNullable(field, fieldValue);
                record.put(field.name, normalizeValue(fieldValue));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * 全量投影策略: 使用 JMESPath 投影一次性提取所有记录的各字段.
     *
     * <p>
     * 当表达式无法拆分为数组路径+字段路径时(如 "[].id" 这种直接数组),
     * 回退到此策略. 此策略要求每个字段的表达式投影结果长度与 isKey 字段一致.
     * </p>
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @param keyField isKey 字段配置
     * @return 记录列表
     */
    private List<Record> mapArrayByProjection(ResponseConfig response, Object json, ApiExtractContext context,
            FieldConfig keyField) {
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
     * 拆分 JMESPath 数组投影表达式为数组路径和字段路径.
     *
     * <p>
     * 例如 "Data[].Today" 拆分为 "Data[]" 和 "Today",
     * "data.list[].id" 拆分为 "data.list[]" 和 "id",
     * "[].id" 返回 null(无法拆分).
     * </p>
     *
     * @param expression JMESPath 表达式
     * @return [数组路径, 字段路径],无法拆分时返回 null
     */
    private String[] splitArrayProjection(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        int bracketIdx = expression.indexOf("[]");
        if (bracketIdx < 0) {
            return null;
        }
        String arrayPath = expression.substring(0, bracketIdx + 2);
        String fieldPath = expression.substring(bracketIdx + 2);
        // 字段路径必须非空且不包含 [] (不支持嵌套投影)
        if (fieldPath.isEmpty() || fieldPath.contains("[]")) {
            return null;
        }
        // 去掉字段路径开头的点号
        if (fieldPath.startsWith(".")) {
            fieldPath = fieldPath.substring(1);
        }
        if (fieldPath.isEmpty()) {
            return null;
        }
        return new String[]{arrayPath, fieldPath};
    }

    /**
     * 在单个数组元素上解析字段值.
     *
     * <p>
     * 优先从元素上直接用字段路径提取值,支持嵌套路径(如 "user.profile.name").
     * 如果表达式中包含数组投影([]),则回退到从完整 json 上用原始表达式提取.
     * </p>
     *
     * @param field 字段配置
     * @param element 单个数组元素
     * @param context 抽取上下文
     * @return 字段值
     */
    private Object resolveFieldOnElement(FieldConfig field, Object element, ApiExtractContext context) {
        if (field.value != null) {
            return templateResolver.resolveObject(field.value, context);
        }
        String expression = field.expression;
        // 如果表达式中包含数组投影([]),去掉数组路径部分,只保留字段路径
        // 例如 "Data[].ProductdetailIdname" 变为 "ProductdetailIdname"
        if (expression.contains("[]")) {
            String[] parts = splitArrayProjection(expression);
            if (parts != null) {
                return evaluator.search(element, parts[1]);
            }
            // 无法拆分时返回 null
            return null;
        }
        return evaluator.search(element, expression);
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

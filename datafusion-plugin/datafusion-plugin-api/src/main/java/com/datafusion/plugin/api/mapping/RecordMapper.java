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
            record.put(field.name, normalizeValue(field, value));
        }
        return record;
    }

    /**
     * 映射数组记录.
     *
     * <p>
     * 从第一个可拆分的数组投影表达式推断数组路径,例如 Data[].id 推断为 Data[]。
     * 后续字段在每个数组元素上逐条提取,避免 JMESPath 投影过滤 null 导致长度不一致。
     * </p>
     *
     * @param response 响应配置
     * @param json 响应 JSON 对象
     * @param context 抽取上下文
     * @return 记录列表
     */
    private List<Record> mapArray(ResponseConfig response, Object json, ApiExtractContext context) {
        String arrayPath = response.fields.stream()
                .map(field -> splitArrayProjection(field.expression))
                .filter(parts -> parts != null)
                .findFirst()
                .map(parts -> parts[0])
                .orElseThrow(() -> new ApiExtractException("ARRAY recordMode requires field expression with []"));
        Object arrayResult = evaluator.search(json, arrayPath);
        if (!(arrayResult instanceof List<?> elements)) {
            throw new ApiExtractException("ARRAY expression must resolve to an array: " + arrayPath);
        }
        List<Record> records = new ArrayList<>(elements.size());
        for (Object element : elements) {
            Record record = new Record();
            for (FieldConfig field : response.fields) {
                Object fieldValue = resolveFieldOnElement(field, element, context);
                record.put(field.name, normalizeValue(field, fieldValue));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * 拆分 JMESPath 数组投影表达式为数组路径和字段路径.
     *
     * <p>
     * 例如 "Data[].Today" 拆分为 "Data[]" 和 "Today",
     * "data.list[].id" 拆分为 "data.list[]" 和 "id",
     * "[].id" 拆分为 "[]" 和 "id".
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
        String arrayPath = expression.substring(0, bracketIdx + 2);
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
        if (TextUtils.isBlank(expression)) {
            return null;
        }
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
     * 标准化值类型,将复杂对象序列化为 JSON 字符串.
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    private Object normalizeValue(FieldConfig field, Object value) {
        if (value instanceof java.util.Map || value instanceof Iterable) {
            return JsonUtils.write(value);
        }
        return value;
    }
}

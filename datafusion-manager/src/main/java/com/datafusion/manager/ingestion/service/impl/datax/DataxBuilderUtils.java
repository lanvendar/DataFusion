package com.datafusion.manager.ingestion.service.impl.datax;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.ingestion.po.IngestionDatasyncFieldEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * DataX builder 工具类.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
public class DataxBuilderUtils {

    private DataxBuilderUtils() {
    }

    /**
     * 读取 JsonNode 字段文本（不存在返回 null）.
     *
     * @param node JsonNode
     * @param field 字段名
     * @return 字段文本
     */
    public static String getText(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    /**
     * 读取 JsonNode 字段为字符串数组（字段为 string 时自动包成单元素数组）.
     *
     * @param node JsonNode
     * @param field 字段名
     * @param om ObjectMapper
     * @return ArrayNode
     */
    public static ArrayNode getTextArray(JsonNode node, String field, ObjectMapper om) {
        ArrayNode arr = om.createArrayNode();
        if (node == null || field == null) {
            return arr;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return arr;
        }
        if (v.isArray()) {
            for (JsonNode e : v) {
                if (e != null && !e.isNull()) {
                    arr.add(e.asText());
                }
            }
            return arr;
        }
        arr.add(v.asText());
        return arr;
    }

    /**
     * 读取数组字段的第一个元素文本（字段为 string 时返回其文本）.
     *
     * @param node JsonNode
     * @param field 字段名
     * @return 文本
     */
    public static String getFirstText(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isArray()) {
            if (v.size() == 0) {
                return null;
            }
            JsonNode first = v.get(0);
            if (first == null || first.isNull()) {
                return null;
            }
            return first.asText();
        }
        return v.asText();
    }

    /**
     * 将 preSql/postSql 字段统一为数组（字符串自动包成数组）.
     *
     * @param config 配置
     * @param field 字段名
     * @param om ObjectMapper
     * @return ArrayNode（可能为空）
     */
    public static ArrayNode getSqlArray(JsonNode config, String field, ObjectMapper om) {
        return getTextArray(config, field, om);
    }

    /**
     * column 混合策略：field 优先 → 回退 config.column；reader querySql 模式下 column = [\"*\"].
     *
     * @param fields 字段映射列表
     * @param config config
     * @param isWriter 是否 writer
     * @param om ObjectMapper
     * @param missingMsg 缺失时错误信息
     * @return column ArrayNode
     */
    public static ArrayNode resolveColumns(List<IngestionDatasyncFieldEntity> fields,
                                           JsonNode config,
                                           boolean isWriter,
                                           ObjectMapper om,
                                           String missingMsg) {
        ArrayNode arr = om.createArrayNode();
        if (CollectionUtil.isNotEmpty(fields)) {
            for (IngestionDatasyncFieldEntity f : fields) {
                arr.add(f.getColumnName());
            }
            return arr;
        }

        if (config != null) {
            JsonNode col = config.get("column");
            if (col != null && col.isArray()) {
                for (JsonNode e : col) {
                    if (e != null && !e.isNull()) {
                        arr.add(e.asText());
                    }
                }
                if (!arr.isEmpty()) {
                    return arr;
                }
            }
        }

        if (!isWriter && config != null && config.has("querySql")) {
            arr.add("*");
            return arr;
        }

        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, missingMsg);
    }

    /**
     * 构建 DataX 节点：{ name, parameter }.
     *
     * @param pluginName 插件名（如 postgresqlreader）
     * @param parameter 参数节点
     * @param om ObjectMapper
     * @return ObjectNode
     */
    public static ObjectNode wrapNameAndParameter(String pluginName, ObjectNode parameter, ObjectMapper om) {
        ObjectNode node = om.createObjectNode();
        node.put("name", pluginName);
        node.set("parameter", parameter);
        return node;
    }
}


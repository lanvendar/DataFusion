package com.datafusion.plugin.flink.table.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.ColumnConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.flink.table.expression.ExpressionEvaluator;
import com.datafusion.plugin.flink.table.expression.ExpressionSpec;
import com.datafusion.plugin.flink.table.core.enums.JsonType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka 标准 schema 解析器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class StandardSchemaParser {

    /**
     * Kafka 标准 schema table 路径.
     */
    static final String SCHEMA_TABLE_PATH = "schema.table";

    /**
     * Kafka 标准 schema columns 路径.
     */
    static final String SCHEMA_COLUMNS_PATH = "schema.columns";

    /**
     * 表达式执行器.
     */
    private final ExpressionEvaluator expressionEvaluator;

    StandardSchemaParser(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    StandardSchema parse(Object messageObject) {
        StandardSchema schema = new StandardSchema();
        Object tableObject = evaluate(messageObject, pathSpec(SCHEMA_TABLE_PATH, JsonType.OBJECT), SCHEMA_TABLE_PATH);
        if (tableObject instanceof Map<?, ?> tableMap) {
            schema.table = tableSchema(copyMap(tableMap));
        }
        Object columnsObject = evaluate(messageObject, pathSpec(SCHEMA_COLUMNS_PATH, JsonType.ARRAY), SCHEMA_COLUMNS_PATH);
        if (columnsObject instanceof Collection<?> collection) {
            schema.columns = columnSchemas(collection);
        }
        return schema;
    }

    private StandardSchema.StandardTableSchema tableSchema(Map<String, Object> table) {
        StandardSchema.StandardTableSchema schema = new StandardSchema.StandardTableSchema();
        schema.database = table.get("database");
        schema.name = table.get("name");
        schema.comment = table.get("comment");
        schema.createIfNotExists = table.get("createIfNotExists");
        schema.partitionKeys = table.get("partitionKeys");
        schema.primaryKeys = primaryKeysConfig(table.get("primaryKeys"));
        return schema;
    }

    private PrimaryKeyConfig primaryKeysConfig(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            PrimaryKeyConfig primaryKey = new PrimaryKeyConfig();
            primaryKey.mode = PrimaryKeyMode.FIELDS.name();
            primaryKey.defaultValue = new ArrayList<>(collection);
            return primaryKey;
        }
        try {
            return JacksonUtils.treeNode2Bean(JacksonUtils.obj2JsonNode(value), PrimaryKeyConfig.class);
        } catch (Exception e) {
            throw new FlinkTableException("Failed to parse schema.table.primaryKeys", e);
        }
    }

    private List<ColumnConfig> columnSchemas(Collection<?> collection) {
        List<ColumnConfig> columns = new ArrayList<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            try {
                ColumnConfig column = JacksonUtils.treeNode2Bean(JacksonUtils.obj2JsonNode(item), ColumnConfig.class);
                columns.add(column.copy());
            } catch (Exception e) {
                throw new FlinkTableException("Failed to parse schema.columns item", e);
            }
        }
        return columns;
    }

    private Object evaluate(Object input, ExpressionSpec spec, String name) {
        return expressionEvaluator.evaluate(input, spec, name);
    }

    private ExpressionSpec pathSpec(String path, JsonType jsonType) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.path = path;
        spec.jsonType = jsonType.name();
        return spec;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }
}

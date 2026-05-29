package com.datafusion.plugin.api.sink.paimon;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.ColumnConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.sink.SinkMode;
import com.datafusion.plugin.api.sink.SinkRecordNormalizer;
import com.datafusion.plugin.api.sink.SinkWriter;
import com.datafusion.plugin.api.util.TextUtils;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.data.BinaryString;
import org.apache.paimon.data.Decimal;
import org.apache.paimon.data.GenericRow;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.data.Timestamp;
import org.apache.paimon.options.Options;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.table.Table;
import org.apache.paimon.table.sink.BatchTableCommit;
import org.apache.paimon.table.sink.BatchTableWrite;
import org.apache.paimon.table.sink.CommitMessage;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.DataType;
import org.apache.paimon.types.DataTypes;
import org.apache.paimon.types.DecimalType;
import org.apache.paimon.types.RowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Paimon 数据写入器.
 *
 * <p>
 * 使用 Paimon API 批量写入数据,支持自动建表和 Schema 校验.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonSinkWriter implements SinkWriter {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonSinkWriter.class);
    
    /**
     * 落表配置.
     */
    private SinkConfig sink;
    
    /**
     * Paimon Catalog.
     */
    private Catalog catalog;
    
    /**
     * 表标识符.
     */
    private Identifier identifier;
    
    /**
     * Paimon 表对象.
     */
    private Table table;
    
    /**
     * 行类型定义.
     */
    private RowType rowType;

    /**
     * 打开写入器并初始化 Catalog 和表.
     *
     * @param sink 落表配置
     */
    @Override
    public void open(SinkConfig sink) {
        this.sink = sink;
        validateMode();
        try {
            LOGGER.info("Paimon sink 开始打开, database={}, table={}, loadMode={}, connectType={}",
                    sink.optionString("database", null), requiredTableName(), sink.loadMode, sink.connectType);
            catalog = CatalogFactory.createCatalog(CatalogContext.create(options()));
            String database = requiredConnection("database");
            catalog.createDatabase(database, true);
            identifier = Identifier.create(database, requiredTableName());
            table = getOrCreateTable(identifier);
            rowType = table.rowType();
            LOGGER.info("Paimon sink 打开完成, identifier={}, fields={}", identifier, rowType.getFieldCount());
        } catch (Exception e) {
            throw new ApiExtractException("Failed to open Paimon sink: " + rootMessage(e), e);
        }
    }

    /**
     * 批量写入记录到 Paimon 表.
     *
     * @param records 记录列表
     */
    @Override
    public void write(List<Record> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Record> normalizedRecords = SinkRecordNormalizer.normalize(records, sink, "Paimon");
        org.apache.paimon.table.sink.BatchWriteBuilder builder = batchWriteBuilder(normalizedRecords);
        long start = System.currentTimeMillis();
        LOGGER.info("Paimon 写入开始, identifier={}, records={}", identifier, records.size());
        try (BatchTableWrite write = builder.newWrite(); BatchTableCommit commit = builder.newCommit()) {
            for (Record record : normalizedRecords) {
                writeRecord(write, toRow(record));
            }
            List<CommitMessage> messages = write.prepareCommit();
            commit.commit(messages);
            LOGGER.info("Paimon 写入完成, identifier={}, records={}, commitMessages={}, elapsedMs={}",
                    identifier, records.size(), messages.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new ApiExtractException("Failed to write Paimon records", e);
        }
    }

    /**
     * 刷新缓冲区(空操作,Paimon 即时提交).
     */
    @Override
    public void flush() {
    }

    /**
     * 关闭写入器并释放 Catalog 资源.
     */
    @Override
    public void close() {
        if (catalog != null) {
            try {
                catalog.close();
            } catch (Exception e) {
                throw new ApiExtractException("Failed to close Paimon catalog", e);
            }
        }
    }

    /**
     * 构建 Paimon Catalog 配置选项.
     *
     * @return 配置选项
     */
    private Options options() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("warehouse", requiredConnection("warehouse"));
        options.put("type", sink.optionString("catalogType", "filesystem"));
        sink.options.forEach((key, value) -> {
            if (value != null) {
                options.put(key, stringValue(value));
            }
        });
        return Options.fromMap(options);
    }

    /**
     * 获取或创建 Paimon 表.
     *
     * @param identifier 表标识符
     * @return Paimon 表对象
     * @throws Exception 异常
     */
    private Table getOrCreateTable(Identifier identifier) throws Exception {
        try {
            Table existing = catalog.getTable(identifier);
            validateSchema(existing);
            return existing;
        } catch (Catalog.TableNotExistException e) {
            if (sink.table == null || !sink.table.createIfNotExists) {
                throw new ApiExtractException("Paimon table does not exist: " + identifier);
            }
            catalog.createTable(identifier, schema(), true);
            return catalog.getTable(identifier);
        }
    }

    private void validateMode() {
        SinkMode mode = SinkMode.parse(sink.loadMode);
        if (mode == SinkMode.UPSERT && (sink.table == null || sink.table.primaryKeys == null || sink.table.primaryKeys.isEmpty())) {
            throw new ApiExtractException("Paimon UPSERT requires sink.table.primaryKeys");
        }
        if (mode == SinkMode.OVERWRITE_PARTITION && (sink.table == null
                || sink.table.partitionKeys == null
                || sink.table.partitionKeys.isEmpty())) {
            throw new ApiExtractException("Paimon OVERWRITE_PARTITION requires sink.table.partitionKeys");
        }
    }

    private org.apache.paimon.table.sink.BatchWriteBuilder batchWriteBuilder(List<Record> records) {
        org.apache.paimon.table.sink.BatchWriteBuilder builder = table.newBatchWriteBuilder();
        if (SinkMode.parse(sink.loadMode) != SinkMode.OVERWRITE_PARTITION) {
            return builder;
        }
        return builder.withOverwrite(overwritePartition(records));
    }

    private Map<String, String> overwritePartition(List<Record> records) {
        Map<String, String> partition = new LinkedHashMap<>();
        for (String key : sink.table.partitionKeys) {
            Object value = null;
            for (Record record : records) {
                Object current = record.get(key);
                if (current == null) {
                    throw new ApiExtractException("Paimon OVERWRITE_PARTITION record lacks partition field: " + key);
                }
                if (value == null) {
                    value = current;
                } else if (!Objects.equals(String.valueOf(value), String.valueOf(current))) {
                    throw new ApiExtractException("Paimon OVERWRITE_PARTITION supports one partition per batch: " + key);
                }
            }
            partition.put(key, String.valueOf(value));
        }
        return partition;
    }

    /**
     * 构建 Paimon Schema.
     *
     * @return Schema 对象
     */
    private Schema schema() {
        Schema.Builder builder = Schema.newBuilder();
        for (ColumnConfig field : sink.columns) {
            builder.column(field.name, paimonType(field), field.comment);
        }
        if (!TextUtils.isBlank(sink.table.comment)) {
            builder.comment(sink.table.comment);
        }
        if (sink.table.partitionKeys != null && !sink.table.partitionKeys.isEmpty()) {
            builder.partitionKeys(sink.table.partitionKeys);
        }
        if (sink.table.primaryKeys != null && !sink.table.primaryKeys.isEmpty()) {
            builder.primaryKey(sink.table.primaryKeys);
        }
        builder.options(tableOptions());
        return builder.build();
    }

    private Map<String, String> tableOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        sink.options.forEach((key, value) -> {
            if (value != null && !isConnectionOption(key)) {
                options.put(key, stringValue(value));
            }
        });
        return options;
    }

    private boolean isConnectionOption(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return "warehouse".equals(normalized)
                || "metastore".equals(normalized)
                || "catalogtype".equals(normalized)
                || "database".equals(normalized)
                || "endpoint".equals(normalized)
                || normalized.startsWith("s3.")
                || normalized.startsWith("fs.s3a.");
    }

    /**
     * 校验表字段与配置的兼容性.
     *
     * @param existing 已存在的表
     */
    private void validateSchema(Table existing) {
        Map<String, DataField> fields = existing.rowType().getFields().stream()
                .collect(Collectors.toMap(field -> field.name().toLowerCase(Locale.ROOT), field -> field));
        for (ColumnConfig configured : sink.columns) {
            DataField actual = fields.get(configured.name.toLowerCase(Locale.ROOT));
            if (actual == null) {
                throw new ApiExtractException("Paimon table lacks configured field: " + configured.name);
            }
            DataType expected = paimonType(configured);
            if (!actual.type().equalsIgnoreNullable(expected)) {
                throw new ApiExtractException("Paimon field type mismatch: " + configured.name
                        + ", expected=" + expected.asSQLString() + ", actual=" + actual.type().asSQLString());
            }
        }
        if (!Objects.equals(existing.primaryKeys(), safeList(sink.table.primaryKeys))) {
            throw new ApiExtractException("Paimon primary keys mismatch");
        }
        if (!Objects.equals(existing.partitionKeys(), safeList(sink.table.partitionKeys))) {
            throw new ApiExtractException("Paimon partition keys mismatch");
        }
        validateTableOptions(existing);
    }

    private void validateTableOptions(Table existing) {
        Map<String, String> expected = tableOptions();
        Map<String, String> actual = existing.options();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String key = entry.getKey();
            String actualValue = actual.get(key);
            if (!Objects.equals(entry.getValue(), actualValue)) {
                throw new ApiExtractException("Paimon table option mismatch: " + key
                        + ", expected=" + entry.getValue() + ", actual=" + actualValue);
            }
        }
    }

    /**
     * 将 Record 转换为 Paimon GenericRow.
     *
     * @param record 输入记录
     * @return Paimon 行对象
     */
    private GenericRow toRow(Record record) {
        GenericRow row = new GenericRow(rowType.getFieldCount());
        for (int i = 0; i < rowType.getFieldCount(); i++) {
            DataField field = rowType.getField(i);
            row.setField(i, convertValue(record.get(field.name()), field.type()));
        }
        return row;
    }

    private void writeRecord(BatchTableWrite write, InternalRow row) throws Exception {
        if (isDynamicBucketTable()) {
            write.write(row, 0);
            return;
        }
        write.write(row);
    }

    private boolean isDynamicBucketTable() {
        return "-1".equals(table.options().getOrDefault("bucket", "-1"));
    }

    /**
     * 转换字段值为 Paimon 数据类型.
     *
     * @param value 原始值
     * @param type Paimon 数据类型
     * @return 转换后的值
     */
    private Object convertValue(Object value, DataType type) {
        if (value == null) {
            return null;
        }
        String root = type.getTypeRoot().name();
        if ("VARCHAR".equals(root) || "CHAR".equals(root)) {
            return BinaryString.fromString(String.valueOf(value));
        }
        if ("INTEGER".equals(root)) {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        }
        if ("BIGINT".equals(root)) {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        }
        if ("DOUBLE".equals(root)) {
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        }
        if ("FLOAT".equals(root)) {
            return value instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(value));
        }
        if ("BOOLEAN".equals(root)) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }
        if ("DECIMAL".equals(root)) {
            DecimalType decimalType = (DecimalType) type;
            return Decimal.fromBigDecimal(new BigDecimal(String.valueOf(value)), decimalType.getPrecision(), decimalType.getScale());
        }
        if ("DATE".equals(root)) {
            LocalDate date = LocalDate.parse(String.valueOf(value));
            return (int) date.toEpochDay();
        }
        if ("TIMESTAMP_WITHOUT_TIME_ZONE".equals(root)) {
            return Timestamp.fromLocalDateTime(parseDateTime(String.valueOf(value)));
        }
        return BinaryString.fromString(String.valueOf(value));
    }

    /**
     * 解析日期时间字符串.
     *
     * @param value 日期时间字符串
     * @return LocalDateTime 对象
     */
    private LocalDateTime parseDateTime(String value) {
        if (value.length() == 19 && value.charAt(10) == ' ') {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(value);
    }

    /**
     * 转换为 Paimon 数据类型.
     *
     * @param field 字段配置
     * @return Paimon 数据类型
     */
    private DataType paimonType(ColumnConfig field) {
        String type = TextUtils.upper(field.type, "STRING");
        DataType dataType;
        if ("STRING".equals(type) || "VARCHAR".equals(type) || "JSON".equals(type)) {
            dataType = field.length == null ? DataTypes.STRING() : DataTypes.VARCHAR(field.length);
        } else if ("INT".equals(type) || "INTEGER".equals(type)) {
            dataType = DataTypes.INT();
        } else if ("BIGINT".equals(type) || "LONG".equals(type)) {
            dataType = DataTypes.BIGINT();
        } else if ("DOUBLE".equals(type)) {
            dataType = DataTypes.DOUBLE();
        } else if ("FLOAT".equals(type)) {
            dataType = DataTypes.FLOAT();
        } else if ("DECIMAL".equals(type)) {
            dataType = DataTypes.DECIMAL(field.precision == null ? 18 : field.precision,
                    field.scale == null ? 4 : field.scale);
        } else if ("BOOLEAN".equals(type)) {
            dataType = DataTypes.BOOLEAN();
        } else if ("DATE".equals(type)) {
            dataType = DataTypes.DATE();
        } else if ("TIMESTAMP".equals(type) || "DATETIME".equals(type)) {
            dataType = DataTypes.TIMESTAMP();
        } else {
            dataType = DataTypes.STRING();
        }
        return dataType.copy(field.nullable);
    }

    /**
     * 获取必需的连接配置项.
     *
     * @param key 配置键
     * @return 配置值
     */
    private String requiredConnection(String key) {
        String value = sink.optionString(key, null);
        if (TextUtils.isBlank(value)) {
            throw new ApiExtractException("Paimon sink.options." + key + " is required");
        }
        return value;
    }

    /**
     * 获取必需的表名.
     *
     * @return 表名
     */
    private String requiredTableName() {
        if (sink.table == null || TextUtils.isBlank(sink.table.name)) {
            throw new ApiExtractException("sink.table.name is required for Paimon");
        }
        return sink.table.name;
    }

    /**
     * 获取异常根因消息.
     *
     * @param throwable 异常
     * @return 根因消息
     */
    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getName() : root.getMessage();
    }

    /**
     * 安全地将 List 转换为非 null 列表.
     *
     * @param values 输入列表
     * @return 非 null 列表
     */
    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    /**
     * 将对象转换为字符串.
     *
     * @param value 待转换的对象
     * @return 字符串表示
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

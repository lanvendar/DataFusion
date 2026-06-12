package com.datafusion.plugin.flink.schema.paimon.sink;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.core.PaimonSchemaMismatchException;
import com.datafusion.plugin.flink.schema.paimon.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableConfig;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;
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
 * 单张 Paimon 表 writer.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonTableWriter implements AutoCloseable {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonTableWriter.class);

    /**
     * catalog 配置.
     */
    private final Map<String, String> catalogOptions;

    /**
     * 目标表配置.
     */
    private final ResolvedTableConfig tableConfig;

    /**
     * 单条记录错误处理策略.
     */
    private final RecordErrorPolicy recordErrorPolicy;

    /**
     * Paimon catalog.
     */
    private Catalog catalog;

    /**
     * 表标识符.
     */
    private Identifier identifier;

    /**
     * Paimon 表.
     */
    private Table table;

    /**
     * 行类型.
     */
    private RowType rowType;

    /**
     * 构造表 writer.
     *
     * @param catalogOptions catalog 配置
     * @param tableConfig 目标表配置
     * @param recordErrorPolicy 单条记录错误处理策略
     */
    public PaimonTableWriter(Map<String, String> catalogOptions, ResolvedTableConfig tableConfig, RecordErrorPolicy recordErrorPolicy) {
        this.catalogOptions = new LinkedHashMap<>(catalogOptions);
        this.tableConfig = tableConfig;
        this.recordErrorPolicy = recordErrorPolicy;
    }

    /**
     * 打开 writer.
     */
    public void open() {
        try {
            catalog = CatalogFactory.createCatalog(CatalogContext.create(catalogOptions()));
            catalog.createDatabase(tableConfig.database, true);
            identifier = Identifier.create(tableConfig.database, tableConfig.table.name);
            table = getOrCreateTable(identifier);
            rowType = table.rowType();
            LOGGER.info("Paimon table writer opened, identifier={}, fields={}", identifier, rowType.getFieldCount());
        } catch (PaimonSchemaMismatchException e) {
            throw e;
        } catch (Exception e) {
            throw new FlinkSchemaPaimonException("Failed to open Paimon table writer: " + tableConfig.identifier(), e);
        }
    }

    /**
     * 批量写入记录.
     *
     * @param records 记录列表
     */
    public void writeBatch(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, Object>> normalizedRecords = RecordNormalizer.normalize(records, tableConfig, recordErrorPolicy);
        if (normalizedRecords.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        LOGGER.info("Paimon write started, identifier={}, records={}", identifier, normalizedRecords.size());
        try (BatchTableWrite write = table.newBatchWriteBuilder().newWrite();
                BatchTableCommit commit = table.newBatchWriteBuilder().newCommit()) {
            int written = 0;
            for (int i = 0; i < normalizedRecords.size(); i++) {
                InternalRow row = toRow(normalizedRecords.get(i), i);
                if (row != null) {
                    writeRecord(write, row);
                    written++;
                }
            }
            if (written == 0) {
                LOGGER.warn("Skip Paimon commit because all records are invalid, identifier={}", identifier);
                return;
            }
            List<CommitMessage> messages = write.prepareCommit();
            commit.commit(messages);
            LOGGER.info("Paimon write finished, identifier={}, records={}, commitMessages={}, elapsedMs={}",
                    identifier, written, messages.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new FlinkSchemaPaimonException("Failed to write Paimon records: " + tableConfig.identifier(), e);
        }
    }

    /**
     * 关闭 writer.
     */
    @Override
    public void close() {
        if (catalog != null) {
            try {
                catalog.close();
            } catch (Exception e) {
                throw new FlinkSchemaPaimonException("Failed to close Paimon catalog: " + tableConfig.identifier(), e);
            }
        }
    }

    private Options catalogOptions() {
        Map<String, String> options = new LinkedHashMap<>(catalogOptions);
        if (TextUtils.isBlank(options.get("type"))) {
            options.put("type", options.getOrDefault("catalogType", "filesystem"));
        }
        return Options.fromMap(options);
    }

    private Table getOrCreateTable(Identifier tableIdentifier) throws Exception {
        try {
            Table existing = catalog.getTable(tableIdentifier);
            validateSchema(existing);
            return existing;
        } catch (Catalog.TableNotExistException e) {
            if (!Boolean.TRUE.equals(tableConfig.table.createIfNotExists)) {
                throw new FlinkSchemaPaimonException("Paimon table does not exist: " + tableIdentifier);
            }
            catalog.createTable(tableIdentifier, schema(), true);
            return catalog.getTable(tableIdentifier);
        }
    }

    private Schema schema() {
        Schema.Builder builder = Schema.newBuilder();
        for (ColumnConfig field : tableConfig.columns) {
            builder.column(field.name, paimonType(field), field.comment);
        }
        if (!TextUtils.isBlank(tableConfig.table.comment)) {
            builder.comment(tableConfig.table.comment);
        }
        if (tableConfig.table.partitionKeys != null && !tableConfig.table.partitionKeys.isEmpty()) {
            builder.partitionKeys(tableConfig.table.partitionKeys);
        }
        if (tableConfig.table.primaryKeys != null && !tableConfig.table.primaryKeys.isEmpty()) {
            builder.primaryKey(tableConfig.table.primaryKeys);
        }
        builder.options(tableOptions());
        return builder.build();
    }

    private void validateSchema(Table existing) {
        Map<String, DataField> fields = existing.rowType().getFields().stream()
                .collect(Collectors.toMap(field -> field.name().toLowerCase(Locale.ROOT), field -> field));
        for (ColumnConfig configured : tableConfig.columns) {
            DataField actual = fields.get(configured.name.toLowerCase(Locale.ROOT));
            if (actual == null) {
                throw new PaimonSchemaMismatchException("Paimon table lacks configured field: " + configured.name);
            }
            DataType expected = paimonType(configured);
            if (!actual.type().equalsIgnoreNullable(expected)) {
                throw new PaimonSchemaMismatchException("Paimon field type mismatch: " + configured.name
                        + ", expected=" + expected.asSQLString() + ", actual=" + actual.type().asSQLString());
            }
            warnCommentMismatch(configured.name, configured.comment, actual.description());
        }
        if (!Objects.equals(existing.primaryKeys(), safeList(tableConfig.table.primaryKeys))) {
            throw new PaimonSchemaMismatchException("Paimon primary keys not match json schema: " + tableConfig.identifier());
        }
        if (!Objects.equals(existing.partitionKeys(), safeList(tableConfig.table.partitionKeys))) {
            throw new PaimonSchemaMismatchException("Paimon partition keys not match json schema: " + tableConfig.identifier());
        }
        validateTableOptions(existing);
        warnCommentMismatch(tableConfig.identifier(), tableConfig.table.comment, existing.comment().orElse(null));
    }

    private void validateTableOptions(Table existing) {
        Map<String, String> actual = existing.options();
        for (Map.Entry<String, String> entry : tableOptions().entrySet()) {
            String actualValue = actual.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), actualValue)) {
                throw new PaimonSchemaMismatchException("Paimon table option mismatch: " + entry.getKey()
                        + ", expected=" + entry.getValue() + ", actual=" + actualValue);
            }
        }
    }

    private Map<String, String> tableOptions() {
        Map<String, String> options = new LinkedHashMap<>(tableConfig.options);
        options.keySet().removeIf(this::isConnectionOption);
        return options;
    }

    private boolean isConnectionOption(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return "warehouse".equals(normalized)
                || "metastore".equals(normalized)
                || "catalogtype".equals(normalized)
                || "type".equals(normalized)
                || "database".equals(normalized)
                || "endpoint".equals(normalized)
                || normalized.startsWith("s3.")
                || normalized.startsWith("fs.s3a.")
                || normalized.startsWith("hadoop.fs.s3a.");
    }

    private void warnCommentMismatch(String objectName, String expected, String actual) {
        if (!Objects.equals(emptyToNull(expected), emptyToNull(actual))) {
            LOGGER.warn("Paimon comment mismatch ignored, object={}, expected={}, actual={}", objectName, expected, actual);
        }
    }

    private GenericRow toRow(Map<String, Object> record, int recordIndex) {
        GenericRow row = new GenericRow(rowType.getFieldCount());
        try {
            for (int i = 0; i < rowType.getFieldCount(); i++) {
                DataField field = rowType.getField(i);
                row.setField(i, convertValue(record.get(field.name()), field.type()));
            }
            return row;
        } catch (RuntimeException e) {
            if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
                throw e;
            }
            LOGGER.warn("Skip Paimon record because value conversion failed, identifier={}, recordIndex={}, reason={}",
                    tableConfig.identifier(), recordIndex, e.getMessage());
            return null;
        }
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
            return (int) LocalDate.parse(String.valueOf(value)).toEpochDay();
        }
        if ("TIMESTAMP_WITHOUT_TIME_ZONE".equals(root)) {
            return Timestamp.fromLocalDateTime(parseDateTime(String.valueOf(value)));
        }
        return BinaryString.fromString(String.valueOf(value));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value.length() == 19 && value.charAt(10) == ' ') {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(value);
    }

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
            dataType = DataTypes.DECIMAL(field.precision == null ? 18 : field.precision, field.scale == null ? 4 : field.scale);
        } else if ("BOOLEAN".equals(type)) {
            dataType = DataTypes.BOOLEAN();
        } else if ("DATE".equals(type)) {
            dataType = DataTypes.DATE();
        } else if ("TIMESTAMP".equals(type) || "DATETIME".equals(type)) {
            dataType = DataTypes.TIMESTAMP();
        } else {
            dataType = DataTypes.STRING();
        }
        return dataType.copy(!Boolean.FALSE.equals(field.nullable));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private String emptyToNull(String value) {
        return TextUtils.isBlank(value) ? null : value;
    }
}

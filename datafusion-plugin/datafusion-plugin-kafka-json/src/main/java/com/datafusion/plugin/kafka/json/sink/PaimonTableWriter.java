package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.data.GenericRow;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.table.Table;
import org.apache.paimon.table.sink.BatchTableCommit;
import org.apache.paimon.table.sink.BatchTableWrite;
import org.apache.paimon.table.sink.CommitMessage;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.RowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            identifier = Identifier.create(tableConfig.database, tableConfig.tableName);
            table = getOrCreateTable(identifier);
            rowType = table.rowType();
            LOGGER.info("Paimon table writer opened, identifier={}, fields={}", identifier, rowType.getFieldCount());
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to open Paimon table writer: " + tableConfig.identifier(), e);
        }
    }

    /**
     * 获取当前 Paimon 表结构快照.
     *
     * @return Paimon 表结构快照
     */
    public PaimonTableSchemaSnapshot schemaSnapshot() {
        return new PaimonTableSchemaSnapshot(table);
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
            throw new KafkaJsonPaimonException("Failed to write Paimon records: " + tableConfig.identifier(), e);
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
                throw new KafkaJsonPaimonException("Failed to close Paimon catalog: " + tableConfig.identifier(), e);
            }
        }
    }

    private Table getOrCreateTable(Identifier tableIdentifier) throws Exception {
        try {
            return catalog.getTable(tableIdentifier);
        } catch (Catalog.TableNotExistException e) {
            if (!Boolean.TRUE.equals(tableConfig.createIfNotExists)) {
                throw new KafkaJsonPaimonException("Paimon table does not exist: " + tableIdentifier);
            }
            catalog.createTable(tableIdentifier, schema(), true);
            return catalog.getTable(tableIdentifier);
        }
    }

    private Schema schema() {
        Schema.Builder builder = Schema.newBuilder();
        for (ColumnConfig field : tableConfig.columns) {
            builder.column(field.name, PaimonTableSchemaValidator.paimonType(field), field.comment);
        }
        if (!TextUtils.isBlank(tableConfig.tableComment)) {
            builder.comment(tableConfig.tableComment);
        }
        if (tableConfig.partitionKeys != null && !tableConfig.partitionKeys.isEmpty()) {
            builder.partitionKeys(tableConfig.partitionKeys);
        }
        if (tableConfig.primaryKeys != null && !tableConfig.primaryKeys.isEmpty()) {
            builder.primaryKey(tableConfig.primaryKeys);
        }
        builder.options(PaimonTableSchemaValidator.tableOptions(tableConfig));
        return builder.build();
    }

    private GenericRow toRow(Map<String, Object> record, int recordIndex) {
        GenericRow row = new GenericRow(rowType.getFieldCount());
        try {
            for (int i = 0; i < rowType.getFieldCount(); i++) {
                DataField field = rowType.getField(i);
                row.setField(i, PaimonValueConverter.convert(record.get(field.name()), field.type()));
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

    private org.apache.paimon.options.Options catalogOptions() {
        return PaimonTableSchemaValidator.catalogOptions(catalogOptions);
    }
}

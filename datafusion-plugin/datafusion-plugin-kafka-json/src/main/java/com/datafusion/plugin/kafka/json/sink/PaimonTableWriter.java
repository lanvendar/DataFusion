package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.Catalog.TableNotExistException;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.data.GenericRow;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.table.Table;
import org.apache.paimon.table.sink.CommitMessage;
import org.apache.paimon.table.sink.StreamTableWrite;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.RowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon 流式单表 writer,只写文件并产出 commit message.
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
     * commit user.
     */
    private final String commitUser;

    /**
     * Paimon catalog.
     */
    private Catalog catalog;

    /**
     * 表标识.
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
     * 流式 writer.
     */
    private StreamTableWrite write;

    /**
     * 构造流式单表 writer.
     *
     * @param catalogOptions catalog 配置
     * @param tableConfig 目标表配置
     * @param recordErrorPolicy 单条记录错误策略
     * @param commitUser commit user
     */
    public PaimonTableWriter(Map<String, String> catalogOptions, ResolvedTableConfig tableConfig,
            RecordErrorPolicy recordErrorPolicy, String commitUser) {
        this.catalogOptions = new LinkedHashMap<>(catalogOptions);
        this.tableConfig = tableConfig;
        this.recordErrorPolicy = recordErrorPolicy;
        this.commitUser = commitUser;
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
            write = table.newStreamWriteBuilder().withCommitUser(commitUser).newWrite();
            LOGGER.info("Paimon table writer opened, identifier={}, fields={}", identifier, rowType.getFieldCount());
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to open Paimon stream table writer: " + tableConfig.identifier(), e);
        }
    }

    /**
     * 获取表结构快照.
     *
     * @return 表结构快照
     */
    public PaimonTableSchemaSnapshot schemaSnapshot() {
        return new PaimonTableSchemaSnapshot(table);
    }

    /**
     * 写入记录.
     *
     * @param records 记录列表
     * @return 成功写入条数
     */
    public int write(List<PaimonRecord> records) {
        List<PaimonRecord> normalizedRecords = RecordNormalizer.normalize(records, tableConfig, recordErrorPolicy);
        int written = 0;
        for (int i = 0; i < normalizedRecords.size(); i++) {
            PaimonRecord record = normalizedRecords.get(i);
            InternalRow row = convertByPaimonRowType(record);
            if (row == null) {
                continue;
            }
            try {
                if (writeRecord(row, record)) {
                    written++;
                }
            } catch (Exception e) {
                throw new KafkaJsonPaimonException("Failed to write Paimon record: " + tableConfig.identifier(), e);
            }
        }
        return written;
    }

    /**
     * 准备提交.
     *
     * @param commitIdentifier 提交编号
     * @return committable
     */
    public PaimonCommittable prepareCommit(long commitIdentifier) {
        try {
            List<CommitMessage> messages = write.prepareCommit(false, commitIdentifier);
            if (messages.isEmpty()) {
                return null;
            }
            PaimonCommittable committable = new PaimonCommittable();
            committable.database = tableConfig.database;
            committable.tableName = tableConfig.tableName;
            committable.commitIdentifier = commitIdentifier;
            committable.commitMessages = messages;
            LOGGER.info("Paimon table prepared commit, identifier={}, commitIdentifier={}, commitMessages={}",
                    identifier, commitIdentifier, messages.size());
            return committable;
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to prepare Paimon commit: " + tableConfig.identifier(), e);
        }
    }

    /**
     * 关闭 writer.
     */
    @Override
    public void close() {
        RuntimeException failure = null;
        if (write != null) {
            try {
                write.close();
            } catch (Exception e) {
                failure = new KafkaJsonPaimonException("Failed to close Paimon stream write: " + tableConfig.identifier(), e);
            }
        }
        if (catalog != null) {
            try {
                catalog.close();
            } catch (Exception e) {
                failure = new KafkaJsonPaimonException("Failed to close Paimon catalog: " + tableConfig.identifier(), e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Table getOrCreateTable(Identifier tableIdentifier) throws Exception {
        try {
            return catalog.getTable(tableIdentifier);
        } catch (TableNotExistException e) {
            validateCreateTableSchema(tableIdentifier);
            catalog.createTable(tableIdentifier, schema(), true);
            return catalog.getTable(tableIdentifier);
        }
    }

    private void validateCreateTableSchema(Identifier tableIdentifier) {
        if (!Boolean.TRUE.equals(tableConfig.createIfNotExists)) {
            throw new KafkaJsonPaimonException("Paimon table does not exist: " + tableIdentifier);
        }
        if (tableConfig.columns == null || tableConfig.columns.isEmpty()) {
            throw new KafkaJsonPaimonException("Paimon create table columns is required: " + tableIdentifier);
        }
        if (tableConfig.partitionKeys == null || tableConfig.partitionKeys.isEmpty()) {
            throw new KafkaJsonPaimonException("Paimon create table partitionKeys is required: " + tableIdentifier);
        }
        if (tableConfig.loadMode == LoadMode.UPSERT && (tableConfig.primaryKeys == null || tableConfig.primaryKeys.isEmpty())) {
            throw new KafkaJsonPaimonException("Paimon create table primaryKeys is required for UPSERT: " + tableIdentifier);
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

    private GenericRow convertByPaimonRowType(PaimonRecord record) {
        GenericRow row = new GenericRow(rowType.getFieldCount());
        try {
            for (int i = 0; i < rowType.getFieldCount(); i++) {
                DataField field = rowType.getField(i);
                Object value = PaimonValueConverter.convert(record.values.get(field.name()), field.type());
                if (value == null && !field.type().isNullable()) {
                    throw new KafkaJsonPaimonException("Required Paimon column is empty: " + field.name());
                }
                row.setField(i, value);
            }
            return row;
        } catch (RuntimeException e) {
            if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
                throw e;
            }
            LOGGER.warn("Skip Paimon record because value conversion failed, identifier={}, topic={}, partition={}, offset={}, "
                            + "recordIndex={}, reason={}",
                    tableConfig.identifier(), record.topic, record.partition, record.offset, record.recordIndex, e.getMessage());
            return null;
        }
    }

    private boolean writeRecord(InternalRow row, PaimonRecord record) throws Exception {
        try {
            if (isDynamicBucketTable()) {
                write.write(row, 0);
                return true;
            }
            write.write(row);
            return true;
        } catch (RuntimeException e) {
            if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
                throw e;
            }
            LOGGER.warn("Skip Paimon record because write failed, identifier={}, topic={}, partition={}, offset={}, recordIndex={}, reason={}",
                    tableConfig.identifier(), record.topic, record.partition, record.offset, record.recordIndex, e.getMessage());
            return false;
        }
    }

    private boolean isDynamicBucketTable() {
        return "-1".equals(table.options().getOrDefault("bucket", "-1"));
    }

    private org.apache.paimon.options.Options catalogOptions() {
        return PaimonTableSchemaValidator.catalogOptions(catalogOptions);
    }
}

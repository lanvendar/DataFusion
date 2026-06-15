package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.WriterConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableWritePlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon 流式 writer 注册表.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonTableWriterRegistry implements AutoCloseable {

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * 写入配置.
     */
    private final WriterConfig writerConfig;

    /**
     * 单条记录错误处理策略.
     */
    private final RecordErrorPolicy recordErrorPolicy;

    /**
     * 表结构缓存.
     */
    private final PaimonTableSchemaCache schemaCache;

    /**
     * commit user.
     */
    private final String commitUser;

    /**
     * writer 缓存.
     */
    private final Map<String, TableWriterHandle> writers = new LinkedHashMap<>();

    /**
     * 构造 writer 注册表.
     *
     * @param sink sink 配置
     * @param commitUser commit user
     */
    public PaimonTableWriterRegistry(PaimonSinkConfig sink, String commitUser) {
        this.sink = sink;
        this.writerConfig = sink.writer == null ? new WriterConfig() : sink.writer;
        this.recordErrorPolicy = RecordErrorPolicy.parse(sink.recordErrorPolicy);
        this.schemaCache = new PaimonTableSchemaCache(sink, SchemaMismatchPolicy.parse(sink.schemaMismatchPolicy));
        this.commitUser = commitUser;
    }

    /**
     * 写入计划.
     *
     * @param plan 写入计划
     */
    public synchronized void write(ResolvedTableWritePlan plan) {
        if (plan == null || plan.records == null || plan.records.isEmpty()) {
            return;
        }
        if (!schemaCache.validate(plan)) {
            return;
        }
        TableWriterHandle handle = writer(plan.tableConfig);
        if (!schemaCache.validate(plan)) {
            removeWriter(plan.tableConfig.identifier(), handle);
            return;
        }
        handle.buffer.addAll(plan.records);
        long now = System.currentTimeMillis();
        if (handle.buffer.size() >= batchSize() || now - handle.lastFlushMs >= flushIntervalMs()) {
            flush(handle);
        }
    }

    /**
     * 刷新缓冲到 writer.
     */
    public synchronized void flushAll() {
        for (TableWriterHandle handle : writers.values()) {
            flush(handle);
        }
    }

    /**
     * 准备提交.
     *
     * @param commitIdentifier 提交编号
     * @return committables
     */
    public synchronized Collection<PaimonCommittable> prepareCommit(long commitIdentifier) {
        flushAll();
        List<PaimonCommittable> committables = new ArrayList<>();
        for (TableWriterHandle handle : writers.values()) {
            PaimonCommittable committable = handle.writer.prepareCommit(commitIdentifier);
            if (committable != null && committable.commitMessages != null && !committable.commitMessages.isEmpty()) {
                committables.add(committable);
            }
        }
        return committables;
    }

    /**
     * 关闭所有 writer.
     */
    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        for (TableWriterHandle handle : writers.values()) {
            try {
                flush(handle);
                handle.writer.close();
            } catch (RuntimeException e) {
                failure = e;
            }
        }
        writers.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private TableWriterHandle writer(ResolvedTableConfig tableConfig) {
        String identifier = tableConfig.identifier();
        TableWriterHandle existing = writers.get(identifier);
        if (existing != null) {
            return existing;
        }
        if (writers.size() >= maxOpenWriters()) {
            throw new KafkaJsonPaimonException("Too many open Paimon writers, maxOpenWriters=" + maxOpenWriters());
        }
        PaimonTableWriter writer = new PaimonTableWriter(sink.globalOptions(), tableConfig, recordErrorPolicy, commitUser);
        writer.open();
        schemaCache.put(identifier, writer.schemaSnapshot());
        TableWriterHandle handle = new TableWriterHandle(writer);
        writers.put(identifier, handle);
        return handle;
    }

    private void removeWriter(String identifier, TableWriterHandle handle) {
        writers.remove(identifier);
        handle.writer.close();
    }

    private void flush(TableWriterHandle handle) {
        if (handle.buffer.isEmpty()) {
            handle.lastFlushMs = System.currentTimeMillis();
            return;
        }
        List<Map<String, Object>> records = new ArrayList<>(handle.buffer);
        handle.buffer.clear();
        handle.writer.write(records);
        handle.lastFlushMs = System.currentTimeMillis();
    }

    private int batchSize() {
        return writerConfig.batchSize == null ? 1000 : writerConfig.batchSize;
    }

    private long flushIntervalMs() {
        return writerConfig.flushIntervalMs == null ? 5000L : writerConfig.flushIntervalMs;
    }

    private int maxOpenWriters() {
        return writerConfig.maxOpenWriters == null ? 256 : writerConfig.maxOpenWriters;
    }

    /**
     * writer 句柄.
     */
    private static class TableWriterHandle {

        /**
         * writer.
         */
        private final PaimonTableWriter writer;

        /**
         * 缓冲记录.
         */
        private final List<Map<String, Object>> buffer = new ArrayList<>();

        /**
         * 最近 flush 时间.
         */
        private long lastFlushMs = System.currentTimeMillis();

        /**
         * 构造句柄.
         *
         * @param writer writer
         */
        private TableWriterHandle(PaimonTableWriter writer) {
            this.writer = writer;
        }
    }
}

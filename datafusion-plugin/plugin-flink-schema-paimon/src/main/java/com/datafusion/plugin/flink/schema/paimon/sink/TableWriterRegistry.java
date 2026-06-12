package com.datafusion.plugin.flink.schema.paimon.sink;

import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonSinkGroupConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.WriteConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableWritePlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon 多表 writer 注册表.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class TableWriterRegistry implements AutoCloseable {

    /**
     * sink 配置.
     */
    private final PaimonSinkGroupConfig sink;

    /**
     * 写入配置.
     */
    private final WriteConfig writeConfig;

    /**
     * writer 缓存.
     */
    private final LinkedHashMap<String, TableWriterHandle> writers = new LinkedHashMap<>(16, 0.75F, true);

    /**
     * 构造 writer 注册表.
     *
     * @param sink sink 配置
     */
    public TableWriterRegistry(PaimonSinkGroupConfig sink) {
        this.sink = sink;
        this.writeConfig = sink.write == null ? new WriteConfig() : sink.write;
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
        TableWriterHandle handle = writer(plan.tableConfig);
        handle.buffer.addAll(plan.records);
        long now = System.currentTimeMillis();
        if (handle.buffer.size() >= batchSize() || now - handle.lastFlushMs >= flushIntervalMs()) {
            flush(handle);
        }
    }

    /**
     * 刷新所有 writer.
     */
    public synchronized void flushAll() {
        for (TableWriterHandle handle : writers.values()) {
            flush(handle);
        }
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
        evictIfNecessary();
        PaimonTableWriter writer = new PaimonTableWriter(sink.globalOptions(), tableConfig);
        writer.open();
        TableWriterHandle handle = new TableWriterHandle(writer);
        writers.put(identifier, handle);
        return handle;
    }

    private void evictIfNecessary() {
        if (writers.size() < maxOpenWriters()) {
            return;
        }
        Map.Entry<String, TableWriterHandle> eldest = writers.entrySet().iterator().next();
        flush(eldest.getValue());
        eldest.getValue().writer.close();
        writers.remove(eldest.getKey());
    }

    private void flush(TableWriterHandle handle) {
        if (handle.buffer.isEmpty()) {
            handle.lastFlushMs = System.currentTimeMillis();
            return;
        }
        List<Map<String, Object>> records = new ArrayList<>(handle.buffer);
        handle.buffer.clear();
        handle.writer.writeBatch(records);
        handle.lastFlushMs = System.currentTimeMillis();
    }

    private int batchSize() {
        return writeConfig.batchSize == null ? 1000 : writeConfig.batchSize;
    }

    private long flushIntervalMs() {
        return writeConfig.flushIntervalMs == null ? 5000L : writeConfig.flushIntervalMs;
    }

    private int maxOpenWriters() {
        return writeConfig.maxOpenWriters == null ? 256 : writeConfig.maxOpenWriters;
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

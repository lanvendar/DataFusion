package com.datafusion.plugin.flink.table.sink;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.resolve.ResolvedTableWritePlan;
import org.apache.flink.api.connector.sink2.CommittingSinkWriter;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paimon committing sink writer.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonCommittingSinkWriter implements CommittingSinkWriter<ResolvedTableWritePlan, PaimonCommittable> {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonCommittingSinkWriter.class);

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * commit user.
     */
    private final String commitUser;

    /**
     * writer 注册表.
     */
    private final PaimonTableWriterRegistry registry;

    /**
     * 提交编号生成器.
     */
    private final AtomicLong commitIdentifierGenerator;

    /**
     * 构造 committing sink writer.
     *
     * @param sink sink 配置
     * @param commitUser commit user
     * @param firstCommitIdentifier 起始提交编号
     */
    public PaimonCommittingSinkWriter(PaimonSinkConfig sink, String commitUser, long firstCommitIdentifier) {
        this.sink = sink;
        this.commitUser = commitUser;
        this.registry = new PaimonTableWriterRegistry(sink, commitUser);
        this.commitIdentifierGenerator = new AtomicLong(firstCommitIdentifier);
    }

    /**
     * 写入单表写入计划.
     *
     * @param element 写入计划
     * @param context sink writer 上下文
     */
    @Override
    public void write(ResolvedTableWritePlan element, SinkWriter.Context context) {
        registry.write(element);
    }

    /**
     * flush writer.
     *
     * @param endOfInput 是否输入结束
     */
    @Override
    public void flush(boolean endOfInput) {
        registry.flushAll();
    }

    /**
     * 准备提交.
     *
     * @return committables
     * @throws IOException 准备提交异常
     */
    @Override
    public Collection<PaimonCommittable> prepareCommit() throws IOException {
        long commitIdentifier = commitIdentifierGenerator.getAndIncrement();
        try {
            Collection<PaimonCommittable> committables = registry.prepareCommit(commitIdentifier);
            LOGGER.info("Paimon writer prepared commit, commitUser={}, commitIdentifier={}, committables={}",
                    commitUser, commitIdentifier, committables.size());
            return committables;
        } catch (RuntimeException e) {
            throw new IOException("Failed to prepare Paimon committables", e);
        }
    }

    /**
     * 关闭 writer.
     */
    @Override
    public void close() {
        registry.close();
    }
}

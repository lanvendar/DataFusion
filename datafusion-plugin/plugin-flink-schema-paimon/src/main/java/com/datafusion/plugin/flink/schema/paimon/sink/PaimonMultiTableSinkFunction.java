package com.datafusion.plugin.flink.schema.paimon.sink;

import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonSinkGroupConfig;
import com.datafusion.plugin.flink.schema.paimon.message.KafkaEnvelope;
import com.datafusion.plugin.flink.schema.paimon.message.MessageParser;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableWritePlan;
import com.datafusion.plugin.flink.schema.paimon.resolve.TableResolver;
import com.datafusion.plugin.flink.schema.paimon.source.KafkaRecord;
import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Kafka schema 消息到 Paimon 多表写入的 Flink sink function.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonMultiTableSinkFunction extends RichSinkFunction<KafkaRecord> implements CheckpointedFunction {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonMultiTableSinkFunction.class);

    /**
     * sink 配置.
     */
    private final PaimonSinkGroupConfig sink;

    /**
     * 消息解析器.
     */
    private transient MessageParser parser;

    /**
     * 表解析器.
     */
    private transient TableResolver resolver;

    /**
     * writer 注册表.
     */
    private transient TableWriterRegistry registry;

    /**
     * 构造 sink function.
     *
     * @param sink sink 配置
     */
    public PaimonMultiTableSinkFunction(PaimonSinkGroupConfig sink) {
        this.sink = sink;
    }

    /**
     * 打开 sink.
     *
     * @param openContext Flink open 上下文
     */
    @Override
    public void open(OpenContext openContext) {
        parser = new MessageParser();
        resolver = new TableResolver(sink);
        registry = new TableWriterRegistry(sink);
        RuntimeContext context = getRuntimeContext();
        TaskInfo taskInfo = context.getTaskInfo();
        LOGGER.info("Paimon multi-table sink opened, subtask={}/{}",
                taskInfo.getIndexOfThisSubtask(), taskInfo.getNumberOfParallelSubtasks());
    }

    /**
     * 处理一条 Kafka 记录.
     *
     * @param value Kafka 记录
     * @param context sink 上下文
     */
    @Override
    public void invoke(KafkaRecord value, SinkFunction.Context context) {
        KafkaEnvelope envelope = parser.parse(value.value);
        Optional<ResolvedTableWritePlan> plan = resolver.resolve(envelope, value);
        plan.ifPresent(registry::write);
    }

    /**
     * checkpoint 时 flush 缓冲.
     *
     * @param context checkpoint 上下文
     */
    @Override
    public void snapshotState(FunctionSnapshotContext context) {
        registry.flushAll();
    }

    /**
     * 初始化 checkpoint state.
     *
     * @param context 初始化上下文
     */
    @Override
    public void initializeState(FunctionInitializationContext context) {
        // 当前 writer registry 为外部 Paimon 写入缓存,不需要恢复本地状态.
    }

    /**
     * 关闭 sink.
     */
    @Override
    public void close() {
        if (registry != null) {
            registry.close();
        }
    }
}

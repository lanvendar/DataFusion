package com.datafusion.plugin.flink.table.sink;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.resolve.ResolvedTableWritePlan;
import org.apache.flink.api.connector.sink2.Committer;
import org.apache.flink.api.connector.sink2.CommitterInitContext;
import org.apache.flink.api.connector.sink2.InitContext;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.SupportsCommitter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.IOException;
import java.util.OptionalLong;

/**
 * Paimon 多表 Sink V2.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonMultiTableSink implements Sink<ResolvedTableWritePlan>, SupportsCommitter<PaimonCommittable> {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * commit user.
     */
    private final String commitUser;

    /**
     * 构造 Paimon 多表 sink.
     *
     * @param sink sink 配置
     * @param commitUser commit user
     */
    public PaimonMultiTableSink(PaimonSinkConfig sink, String commitUser) {
        this.sink = sink;
        this.commitUser = commitUser;
    }

    /**
     * 创建 writer.
     *
     * @param context writer 初始化上下文
     * @return sink writer
     * @throws IOException 创建异常
     */
    @Override
    public SinkWriter<ResolvedTableWritePlan> createWriter(WriterInitContext context) throws IOException {
        return new PaimonCommittingSinkWriter(sink, commitUser, firstCommitIdentifier(context));
    }

    /**
     * 创建 committer.
     *
     * @param context committer 初始化上下文
     * @return committer
     * @throws IOException 创建异常
     */
    @Override
    public Committer<PaimonCommittable> createCommitter(CommitterInitContext context) throws IOException {
        return new PaimonCommitter(sink, commitUser);
    }

    /**
     * 获取 committable 序列化器.
     *
     * @return committable 序列化器
     */
    @Override
    public SimpleVersionedSerializer<PaimonCommittable> getCommittableSerializer() {
        return new PaimonCommittableSerializer();
    }

    private long firstCommitIdentifier(InitContext context) {
        OptionalLong restoredCheckpointId = context.getRestoredCheckpointId();
        if (restoredCheckpointId.isPresent()) {
            return restoredCheckpointId.getAsLong() + 1;
        }
        return InitContext.INITIAL_CHECKPOINT_ID;
    }
}

package com.datafusion.plugin.flink.table.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.flink.table.source.KafkaRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Kafka JSON 消息解析为 Paimon 单表写入计划.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class FlinkTableToPaimonWritePlanFunction extends RichFlatMapFunction<KafkaRecord, ResolvedTableWritePlan> {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlinkTableToPaimonWritePlanFunction.class);

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * 表解析器.
     */
    private transient TableResolver resolver;

    /**
     * 构造解析函数.
     *
     * @param sink sink 配置
     */
    public FlinkTableToPaimonWritePlanFunction(PaimonSinkConfig sink) {
        this.sink = sink;
    }

    /**
     * 打开解析器.
     *
     * @param openContext Flink open 上下文
     */
    @Override
    public void open(OpenContext openContext) {
        resolver = new TableResolver(sink);
    }

    /**
     * 解析 Kafka JSON 记录.
     *
     * @param value Kafka 记录
     * @param out 输出收集器
     */
    @Override
    public void flatMap(KafkaRecord value, Collector<ResolvedTableWritePlan> out) {
        Optional<ResolvedTableWritePlan> plan;
        try {
            JsonNode message = JacksonUtils.str2JsonNode(value.value);
            plan = resolver.resolve(message, value);
        } catch (Exception e) {
            if (RecordErrorPolicy.parse(sink.recordErrorPolicy) == RecordErrorPolicy.FAIL) {
                throw new FlinkTableException("Failed to parse or resolve Kafka JSON message", e);
            }
            LOGGER.warn("Skip Kafka JSON message because parsing or resolving failed, topic={}, partition={}, offset={}, reason={}",
                    value.topic, value.partition, value.offset, e.getMessage());
            return;
        }
        plan.ifPresent(out::collect);
    }
}

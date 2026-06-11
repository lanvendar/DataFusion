package com.datafusion.plugin.flink.schema.paimon.message;

import java.io.Serializable;

/**
 * Kafka 消息可选元信息.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class MessageMeta implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 事件时间.
     */
    public String eventTime;

    /**
     * 追踪 ID.
     */
    public String traceId;
}

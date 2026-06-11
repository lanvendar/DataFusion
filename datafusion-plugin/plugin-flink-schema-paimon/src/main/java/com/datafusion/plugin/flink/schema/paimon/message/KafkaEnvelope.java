package com.datafusion.plugin.flink.schema.paimon.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka 输入消息.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaEnvelope implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 消息 schema.
     */
    public MessageSchema schema = new MessageSchema();

    /**
     * 数据数组.
     */
    public List<Map<String, Object>> data = new ArrayList<>();

    /**
     * 可选元信息.
     */
    public MessageMeta meta = new MessageMeta();

    /**
     * 创建空记录.
     *
     * @return 空记录
     */
    public static Map<String, Object> record() {
        return new LinkedHashMap<>();
    }
}

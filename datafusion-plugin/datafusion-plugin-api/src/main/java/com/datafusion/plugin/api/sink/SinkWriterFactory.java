package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.sink.paimon.PaimonSinkWriter;
import com.datafusion.plugin.api.sink.starrocks.StarRocksSinkWriter;
import com.datafusion.plugin.api.util.TextUtils;

/**
 * SinkWriter 工厂类.
 *
 * <p>
 * 根据配置创建对应的 SinkWriter 实现.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class SinkWriterFactory {
    
    /**
     * 创建 SinkWriter 实例.
     *
     * @param sink 落表配置
     * @return SinkWriter 实例
     * @throws ApiExtractException 不支持的 sink 类型时抛出
     */
    public SinkWriter create(SinkConfig sink) {
        String type = TextUtils.upper(sink.type, null);
        if ("STARROCKS".equals(type)) {
            return new StarRocksSinkWriter();
        }
        if ("PAIMON".equals(type)) {
            return new PaimonSinkWriter();
        }
        if ("NOOP".equals(type)) {
            return new NoopSinkWriter();
        }
        throw new ApiExtractException("Unsupported sink.type: " + sink.type);
    }
}

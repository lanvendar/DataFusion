package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.util.TextUtils;

/**
 * Sink 写入模式.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public enum SinkMode {
    /** 追加写入. */
    APPEND,

    /** 主键更新写入. */
    UPSERT,

    /** 覆盖指定分区. */
    OVERWRITE_PARTITION;

    /**
     * 解析写入模式.
     *
     * @param value 配置值
     * @return 写入模式
     */
    public static SinkMode parse(String value) {
        String normalized = TextUtils.upper(value, "APPEND");
        try {
            return SinkMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ApiExtractException("Unsupported sink.loadMode: " + value);
        }
    }
}

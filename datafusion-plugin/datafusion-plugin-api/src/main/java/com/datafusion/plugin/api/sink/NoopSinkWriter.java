package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.Record;

import java.util.List;

/**
 * 空写入器,用于本地调试字段映射链路.
 *
 * <p>
 * 不实际写入数据,仅统计记录数.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class NoopSinkWriter implements SinkWriter {
    
    /**
     * 已接收记录数.
     */
    private long records;

    /**
     * 打开写入器并初始化.
     *
     * @param sink 落表配置
     */
    @Override
    public void open(SinkConfig sink) {
        records = 0;
    }

    /**
     * 写入记录(仅计数).
     *
     * @param records 记录列表
     */
    @Override
    public void write(List<Record> records) {
        this.records += records.size();
    }

    /**
     * 刷新缓冲区(空操作).
     */
    @Override
    public void flush() {
        records = 0;
    }
}

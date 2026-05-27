package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.Record;

import java.util.List;

/**
 * 数据写入器接口,用于将抽取的记录写入目标存储系统.
 *
 * <p>
 * 支持 StarRocks、Paimon 等多种目标存储,通过工厂模式创建具体实现.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SinkWriter extends AutoCloseable {
    
    /**
     * 打开写入器并初始化连接.
     *
     * @param sink 目标存储配置
     */
    void open(SinkConfig sink);

    /**
     * 批量写入记录到目标存储.
     *
     * @param records 待写入的记录列表
     */
    void write(List<Record> records);

    /**
     * 刷新缓冲区,确保所有记录已持久化.
     */
    void flush();

    /**
     * 关闭写入器并释放资源.
     */
    @Override
    default void close() {
    }
}

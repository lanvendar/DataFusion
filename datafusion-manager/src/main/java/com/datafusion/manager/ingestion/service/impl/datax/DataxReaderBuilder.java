package com.datafusion.manager.ingestion.service.impl.datax;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * DataX reader 构建器.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
public interface DataxReaderBuilder {

    /**
     * 支持的 source_ds_type（小写）.
     *
     * @return source_ds_type
     */
    String supports();

    /**
     * 构建 reader 节点（包含 name 与 parameter）.
     *
     * @param ctx 上下文
     * @return reader 节点
     */
    ObjectNode buildReader(DataxJobContext ctx);
}


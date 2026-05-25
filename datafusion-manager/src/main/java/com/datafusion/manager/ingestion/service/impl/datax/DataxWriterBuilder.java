package com.datafusion.manager.ingestion.service.impl.datax;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * DataX writer 构建器.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
public interface DataxWriterBuilder {

    /**
     * 支持的 target_ds_type（小写）.
     *
     * @return target_ds_type
     */
    String supports();

    /**
     * 构建 writer 节点（包含 name 与 parameter）.
     *
     * @param ctx 上下文
     * @return writer 节点
     */
    ObjectNode buildWriter(DataxJobContext ctx);
}


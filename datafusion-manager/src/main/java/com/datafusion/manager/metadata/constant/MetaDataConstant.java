package com.datafusion.manager.metadata.constant;

/**
 * 元数据相关常量.
 *
 * @author weibo
 * @version 1.0.0 , 2026/04/27
 * @since 2026/04/27
 */
public class MetaDataConstant {

    /**
     * 私有构造函数,防止外部创建实例.
     */
    private MetaDataConstant() {
        throw new IllegalStateException("static constant class");
    }

    /**
     * MaxCompute批量获取元数据的批次大小.
     */
    public static final int MAXCOMPUTE_META_BATCH_SIZE = 200;

    /**
     * 批量插入元数据的批次带下.
     */
    public static final int META_BATCH_SIZE = 1000;
}
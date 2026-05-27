package com.datafusion.plugin.api.core;

import java.util.LinkedHashMap;

/**
 * 数据记录类,表示从 API 响应中抽取的一条结构化数据.
 *
 * <p>
 * 基于 LinkedHashMap 实现,保持字段插入顺序,便于序列化和落表.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class Record extends LinkedHashMap<String, Object> {
}

package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 元数据信息.
 *
 * @author david
 * @version 3.6.4, 2024/8/13
 * @since 3.6.4, 2024/8/13
 */
@Data
public class MetaDataInfo {
    
    /**
     * 空的MetaDataInfo对象.
     */
    public static final MetaDataInfo EMPTY_META_DATA_INFO = new MetaDataInfo();

    /**
     * 数据表信息.
     */
    private List<TableInfo> tables = new ArrayList<>();

    /**
     * 表字段信息.
     */
    private Map<String, List<TableColumnInfo>> columns = new HashMap<>();

    /**
     * 表数量.
     */
    private long tableCount;

    /**
     * 获取表格的数量.
     * 此方法用于获取当前环境中表格的总数如果表格集合为空，则返回0
     *
     * @return 表格的数量
     */
    public long getTableCount() {
        return Optional.ofNullable(tables)
                .map(List::size)
                .orElse(0);
    }
}

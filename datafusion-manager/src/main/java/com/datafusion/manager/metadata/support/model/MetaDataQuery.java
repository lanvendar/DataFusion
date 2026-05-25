package com.datafusion.manager.metadata.support.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 元数据查询条件模型.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/9/1
 * @since 2025/9/1
 */
@Data
@Accessors(chain = true)
public class MetaDataQuery {
    /**
     * 表名前缀,用于模糊匹配表名.
     */
    private String tableNamePrefix;
    
    /**
     * 表名集合,用于精确匹配一组表.
     */
    private List<String> tableNames;
    
    //TODO 未来可以扩展，比如按表类型过滤等
}

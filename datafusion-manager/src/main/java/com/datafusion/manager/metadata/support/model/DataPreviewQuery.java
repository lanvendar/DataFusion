package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.List;

/**
 * 数据预览查询条件.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
@Data
public class DataPreviewQuery {
    /**
     * 表名.
     */
    String tableName;
    
    /**
     * 查询字段.
     */
    List<SelectListColumn> columns;
    
    /**
     * 查询条件.
     * eg:
     * List[String] conditions = Arrays.asList("ts > 1000 and ts < 2000", "name like '%ctg%',...);
     * 拼接成String whereSql = "where ts > 1000 and ts < 2000 and "name like '%ctg%' and  ...".
     */
    List<String> queryConditions;
    
    /**
     * 排序条件.
     * eg:
     * List[String] orderConditions = Arrays.asList("ts desc", "name asc",...);
     * 拼接成String orderSql = "order by ts desc, name asc, ...".
     */
    List<String> orderConditions;
    
    /**
     * 查询数量.
     */
    int limit;
}

package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 元数据表预览查询条件.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/5
 * @since 3.7.2, 2024/11/5
 */
@Data
public class TableBusMetaDataQueryDto {

    /**
     * 数据库表ID.
     */
    @Schema(name = "tableId", description = "数据库表ID")
    private UUID tableId;

    /**
     * 查询条件.
     * eg:
     * List[String] conditions = Arrays.asList("ts > 1000 and ts < 2000", "name like '%ctg%',...);
     * 拼接成String whereSql = "where ts > 1000 and ts < 2000 and "name like '%ctg%' and  ...".
     */
    List<String> queryConditions;

    /**
     * 排序参数.
     *      * eg:
     *      * List[String] conditions = Arrays.asList("ts > 1000 and ts < 2000", "name like '%ctg%',...);
     *      * 拼接成String whereSql = "where ts > 1000 and ts < 2000 and "name like '%ctg%' and  ...".
     */
    List<String> orderConditions;

    /**
     * 每页数量.
     */
    @Schema(name = "limit", description = "每页数量")
    private int limit = 10;
}

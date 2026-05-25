package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 对比操作日志查询接口.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/18
 * @since 2025/9/18
 */
@Data
@Schema(name = "MetadataTableOperateLogQueryDto", description = "对比操作日志查询Dto")
public class MetadataTableOperateLogQueryDto {
    
    /**
     * 源数据库名称.
     */
    @Schema(name = "sourceDatabaseName", description = "源数据库名称")
    private String sourceDatabaseName;
    
    /**
     * 目标数据库名称.
     */
    @Schema(name = "targetDatabaseName", description = "目标数据库名称")
    private String targetDatabaseName;
    
    /**
     * 操作类型：0:批量创建|1:批量对比.
     */
    @Schema(name = "operateType", description = "操作类型：0:批量创建|1:批量对比")
    private Integer operateType;
    
    /**
     * 操作日期.
     */
    @Schema(name = "operateTime", description = "操作日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date operateTime;
}

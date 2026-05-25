package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 元数据-数据源复制Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/13
 * @since 3.6.4, 2024/8/13
 */
@Data
@Schema(name = "DataSourceInfoCopyDto", description = "元数据-数据源复制Dto,针对数据源IP,db相同")
public class DataSourceInfoCopyDto {

    /**
     * 数据源id.
     */
    @Schema(name = "id", description = "数据源id")
    @NotNull(message = "原数据源id不能为空")
    private UUID id;

    /**
     * 数据源id.
     */
    @Schema(name = "name", description = "数据源名称,copy后名称必须修改和以前名称不同")
    @NotEmpty(message = "数据源名称")
    private String name;

    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称,db和schemaName不能同时和以前相同")
    @NotEmpty(message = "schemaName不能为空")
    private String schemaName;

    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    @NotEmpty(message = "databaseName不能为空")
    private String databaseName;

    /**
     * 数据库编码.
     */
    @Schema(name = "characterEncoding", description = "数据库编码", defaultValue = "UTF-8", hidden = true)
    private String characterEncoding = "UTF-8";

    /**
     * 是否自动连接.
     */
    @Schema(name = "autoReconnect", description = "是否自动连接", defaultValue = "true", hidden = true)
    private Boolean autoReconnect = true;

    /**
     * 是否ssl连接.
     */
    @Schema(name = "useSsl", description = "是否ssl连接", defaultValue = "true", hidden = true)
    private boolean useSsl;

    /**
     * 时区.
     */
    @Schema(name = "serverTimezone", description = "时区", defaultValue = "Asia/Shanghai", hidden = true)
    private String serverTimezone = "Asia/Shanghai";

    /**
     * 元数据配置-hive、starrocks.
     */
    @Schema(name = "metadataInfo", description = "元数据,目前只有hive有值")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode metadataInfo;

    /**
     * 拓展信息.
     */
    @Schema(description = "元数据,- 拓展信息，autoReconnect：是否自动连接、useSsl：是否ssl连接、serverTimezone：时区")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode extendParam;
}

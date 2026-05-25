package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Properties;

/**
 * 元数据-数据源新增Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/13
 * @since 3.6.4, 2024/8/13
 */
@Data
@Schema(name = "DataSourceInfoSaveDto", description = "元数据-数据源新增Dto")
public class DataSourceInfoSaveDto {

    /**
     * 数据连接名称.
     */
    @Schema(name = "name", description = "数据连接名称")
    @NotEmpty(message = "数据库名称不能为空")
    private String name;

    /**
     * 数据库主机地址.
     */
    @Schema(name = "host", description = "数据库主机地址")
    @NotEmpty(message = "host不能为空")
    private String host;

    /**
     * 数据库主机端口.
     */
    @Schema(name = "port", description = "数据库主机端口")
    @NotNull(message = "port不能为空")
    private Integer port;

    /**
     * 账号.
     */
    @Schema(name = "username", description = "账号")
    private String username;

    /**
     * 密码.
     */
    @Schema(name = "password", description = "密码")
    private String password;

    /**
     * 数据库类型.
     */
    @Schema(name = "databaseType", description = "数据库类型")
    @NotEmpty(message = "databaseType不能为空")
    private String databaseType;

    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称")
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
    private Properties extendParam;

}

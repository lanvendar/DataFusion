package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Properties;
import java.util.UUID;

/**
 * 数据源信息DTO.
 *
 * @author david
 * @version 3.6.4, 2024/8/15
 * @since 3.6.4, 2024/8/15
 */
@Data
@Schema(name = "DataSourceInfoDto", description = "数据源信息DTO")
public class DataSourceInfoDto {
    
    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;
    
    /**
     * 数据连接名称.
     */
    @Schema(name = "name", description = "数据连接名称")
    private String name;
    
    /**
     * 数据库主机地址.
     */
    @Schema(name = "host", description = "数据库主机地址")
    private String host;
    
    /**
     * 数据库主机端口.
     */
    @Schema(name = "port", description = "数据库主机端口")
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
    
    /*
     * 密码.
     *
     * @param password 将查询接口,密码直接返回********
     */
    /*public void setPassword(String password) {
        this.password = DesensitizedUtil.password(password);
    }*/
    
    /**
     * 数据库类型.
     */
    @Schema(name = "databaseType", description = "数据库类型")
    private String databaseType;
    
    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称")
    private String schemaName;
    
    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    private String databaseName;
    
    /**
     * 数据库编码.
     */
    @Schema(name = "databaseEncode", description = "数据库编码")
    private String databaseEncode;
    
    /**
     * JDBC连接.
     */
    @Schema(name = "jdbcUrl", description = "JDBC连接")
    private String jdbcUrl;
    
    /**
     * 连接方式.
     */
    @Schema(name = "connectType", description = "连接方式")
    private String connectType;
    
    /**
     * 驱动类.
     */
    @Schema(name = "driverClass", description = "驱动类")
    private String driverClass;
    
    /**
     * 元数据,目前只有hive有值.
     */
    @Schema(name = "metadataInfo", description = "元数据,目前只有hive有值")
    private JsonNode metadataInfo;
    
    /**
     * 拓展信息.
     */
    @Schema(description = "元数据,- 拓展信息，autoReconnect：是否自动连接、useSsl：是否ssl连接、serverTimezone：时区")
    @JsonSetter(nulls = Nulls.SKIP)
    private Properties extendParam;
    
    /**
     * 表数量.
     */
    @Schema(name = "tableCount", description = "表数量")
    private Integer tableCount;
    
    /**
     * 同步数量.
     */
    @Schema(name = "syncCount", description = "同步数量")
    private Integer syncCount;
}

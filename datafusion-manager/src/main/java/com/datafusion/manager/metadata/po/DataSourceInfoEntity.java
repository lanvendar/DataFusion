package com.datafusion.manager.metadata.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import com.datafusion.common.web.typehandler.PropertiesTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Properties;

/**
 * 元数据-数据源实体.
 *
 * @author david
 * @version 3.6.4, 2024/8/13
 * @since 3.6.4, 2024/8/13
 */
@Data
@TableName("metadata_datasource_info")
public class DataSourceInfoEntity extends BaseIdEntity {

    /**
     * 数据源名称.
     */
    @TableField("name")
    private String name;

    /**
     * 数据库主机地址.
     */
    @TableField("host")
    private String host;

    /**
     * 数据库主机端口.
     */
    @TableField("port")
    private Integer port;

    /**
     * 账号.
     */
    @TableField("username")
    private String username;

    /**
     * 密码.
     */
    @TableField("password")
    private String password;

    /**
     * 数据库类型.
     */
    @TableField("database_type")
    private String databaseType;

    /**
     * 数据库schema名称.
     */
    @TableField("schema_name")
    private String schemaName;

    /**
     * 数据库名称.
     */
    @TableField("database_name")
    private String databaseName;

    /**
     * 数据库编码.
     */
    @TableField("database_encode")
    private String databaseEncode;

    /**
     * JDBC连接.
     */
    @TableField("jdbc_url")
    private String jdbcUrl;

    /**
     * 连接方式.
     */
    @TableField("connect_type")
    private String connectType;

    /**
     * 驱动类.
     */
    @TableField("driver_class")
    private String driverClass;

    /**
     * 元数据,目前只有hive、starrocks有值.
     */
    @TableField("metadata_info")
    private JsonNode metadataInfo;

    /**
     * 拓展参数.
     */
    @TableField(value = "extend_param", typeHandler = PropertiesTypeHandler.class)
    private Properties extendParam;

    /**
     * 表数量.
     */
    @TableField("table_count")
    private long tableCount;

}

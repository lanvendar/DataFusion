package com.datafusion.datasource.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;


/**
 * 数据库连接信息 DataSourceInfo 对象.
 *
 * @author lanvendar
 * @version V1.0.0, 2021/12/30
 * @since 2021/12/30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceInfo implements Serializable {
    
    private static final long serialVersionUID = 4416530641631013062L;
    
    /**
     * 主键.
     */
    private UUID id;
    
    /**
     * 数据库类型.
     *
     * @see com.datafusion.common.enums.DatabaseTypeEnum
     */
    private String databaseType;
    
    /**
     * 数据库IP地址.
     */
    private String host;
    
    /**
     * 数据库端口号.
     */
    private Integer port;
    
    /**
     * 数据库schema名称.
     */
    private String schemaName;
    
    /**
     * 数据库名称.
     */
    private String databaseName;
    
    /**
     * 数据库编码.
     */
    private String databaseEncode;
    
    /**
     * jdbc连接串.
     */
    private String jdbcUrl;
    
    /**
     * 连接方式.
     *
     * @see com.datafusion.common.enums.ConnectTypeEnum
     */
    private String connectType;
    
    /**
     * 驱动名称.
     *
     * @see com.datafusion.common.enums.DatabaseTypeEnum
     */
    private String driverClass;
    
    /**
     * 登录用户.
     */
    private String username;
    
    /**
     * 登录密码.
     */
    private String password;
    
    /**
     * 元数据信息.
     */
    private JsonNode metadataInfo;
    
    /**
     * 表数量.
     */
    private Long tableCount;
    
    /**
     * 表同步数量.
     */
    private int syncCount;

    /**
     * 拓展参数.
     */
    private Properties extendParam;
    
    /**
     * 数据源基本信息构造器.
     *
     * @param host     ip
     * @param port     port
     * @param username username
     * @param password password
     */
    
    public DataSourceInfo(String host, Integer port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        DataSourceInfo other = (DataSourceInfo) obj;
        return Objects.equals(id, other.id) && Objects.equals(databaseType, other.databaseType) && Objects.equals(host,
                other.host) && Objects.equals(port, other.port) && Objects.equals(schemaName, other.schemaName)
                && Objects.equals(databaseName, other.databaseName) && Objects.equals(databaseEncode,
                other.databaseEncode) && Objects.equals(jdbcUrl, other.jdbcUrl) && Objects.equals(connectType,
                other.connectType) && Objects.equals(driverClass, other.driverClass) && Objects.equals(username,
                other.username) && Objects.equals(password, other.password) && Objects.equals(metadataInfo,
                other.metadataInfo);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, databaseType, host, port, schemaName, databaseName, databaseEncode, jdbcUrl, connectType,
                driverClass, username, password, metadataInfo);
    }
}

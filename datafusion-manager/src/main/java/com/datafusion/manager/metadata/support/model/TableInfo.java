package com.datafusion.manager.metadata.support.model;

import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import lombok.Data;

import java.io.Serializable;
import java.util.Properties;
import java.util.UUID;

/**
 * 表信息标准化模型.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/9/12
 * @since 2023/9/12
 */
@Data
public class TableInfo implements Serializable {
    
    private static final long serialVersionUID = -351044574796188799L;
    
    /**
     * 主键.
     */
    private UUID tableId;
    
    /**
     * 数据库连接信息关联键.
     */
    private UUID schemaId;
    
    /**
     * 表名.
     */
    private String tableName;
    
    /**
     * 表注释.
     */
    private String tableDesc;
    
    /**
     * 表类型.
     */
    private String tableType;
    
    /**
     * 表属性.
     */
    private Properties tableProperties;
    
    /**
     * 是否可修改:true是,false否.
     */
    private Boolean isModify;
    
    /**
     * 是否视图:true是,false否.
     */
    private Boolean isView;
    
    /**
     * 视图定义.
     */
    private String viewDef;

    /**
     * 获取分区信息.
     * @return String String
     */
    public String getPartitionKeys() {
        if (tableProperties != null && tableProperties.containsKey(TablePropertiesOptions.PARTITION_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.PARTITION_KEYS.key());
        }
        return null;
    }

    /**
     * 获取分桶信息.
     * @return String String
     */
    public String getBucketKeys() {
        if (tableProperties != null && tableProperties.containsKey(TablePropertiesOptions.BUCKET_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.BUCKET_KEYS.key());
        }
        return null;
    }

    /**
     * 获取主键信息.
     * @return String String
     */
    public String getPrimaryKeys() {
        if (tableProperties != null && tableProperties.containsKey(TablePropertiesOptions.PRIMARY_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.PRIMARY_KEYS.key());
        }
        return null;
    }
}

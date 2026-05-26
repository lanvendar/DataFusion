package com.datafusion.manager.metadata.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseEntity;
import com.datafusion.common.spring.typehandler.PropertiesTypeHandler;
import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

/**
 * 元数据-表历史版本实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Data
@TableName("metadata_table_info_his")
public class TableInfoHisIdEntity extends BaseEntity {

    /**
     * 物理主键ID.
     */
    @TableField("id")
    private UUID id;

    /**
     * 数据库连接ID.
     */
    @TableField("datasource_id")
    private UUID datasourceId;

    /**
     * 版本.
     */
    @TableField("version")
    private String version;

    /**
     * 表名称.
     */
    @TableField("table_name")
    private String tableName;

    /**
     * 表注释.
     */
    @TableField("table_desc")
    private String tableDesc;

    /**
     * 表属性. 存放表一些特有的属性
     */
    @TableField(value = "table_properties", typeHandler = PropertiesTypeHandler.class)
    private Properties tableProperties;

    /**
     * 是否可修改.
     */
    @TableField("is_modify")
    private Boolean isModify;

    /**
     * 是否视图.
     */
    @TableField("is_view")
    private Boolean isView;

    /**
     * 视图定义.
     */
    @TableField("view_def")
    private String viewDef;

    /**
     * 表同步检查时间.
     */
    @TableField("check_time")
    protected LocalDateTime checkTime;

    /**
     * 表结构是否一致.
     */
    @TableField("is_equal")
    private Boolean isEqual;

    /**
     * 获取分区信息.
     * @return String String
     */
    public String getPartitionKeys() {
        if (tableProperties != null && tableProperties.contains(TablePropertiesOptions.PARTITION_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.PARTITION_KEYS.key());
        }
        return null;
    }

    /**
     * 获取分桶信息.
     * @return String String
     */
    public String getBucketKeys() {
        if (tableProperties != null && tableProperties.contains(TablePropertiesOptions.BUCKET_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.BUCKET_KEYS.key());
        }
        return null;
    }

    /**
     * 获取主键信息.
     * @return String String
     */
    public String getPrimaryKeys() {
        if (tableProperties != null && tableProperties.contains(TablePropertiesOptions.PRIMARY_KEYS.key())) {
            return tableProperties.getProperty(TablePropertiesOptions.PRIMARY_KEYS.key());
        }
        return null;
    }

}

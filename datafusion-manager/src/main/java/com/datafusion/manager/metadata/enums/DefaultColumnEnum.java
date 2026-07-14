package com.datafusion.manager.metadata.enums;

import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 默认字段枚举.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/9/23
 * @since 2025/9/23
 */
@Getter
public enum DefaultColumnEnum {
    
    /**
     * MaxCompute 默认字段：分区客户节点编码.
     */
    MAXCOMPUTE_DISTRIBUTED_CODE(DatabaseTypeEnum.MAXCOMPUTE.name().toLowerCase() + "_distributed_code", true, DatabaseTypeEnum.MAXCOMPUTE,
            () -> new ColumnInfoEntity()//
                    .setColumnName("distributed_code")//
                    .setColumnDesc("客户分区编码")//
                    .setColumnType("STRING")),
    /**
     * MaxCompute 默认字段：分区时区字段.
     */
    MAXCOMPUTE_DISTZ(DatabaseTypeEnum.MAXCOMPUTE.name().toLowerCase() + "_distz", true, DatabaseTypeEnum.MAXCOMPUTE,
            () -> new ColumnInfoEntity()//
                    .setColumnName("distz")//
                    .setColumnDesc("时区:格式-12to12")//
                    .setColumnType("STRING")),
    /**
     * MaxCompute 默认字段：分区日期字段.
     */
    MAXCOMPUTE_DS(DatabaseTypeEnum.MAXCOMPUTE.name().toLowerCase() + "_ds", true, DatabaseTypeEnum.MAXCOMPUTE,
            () -> new ColumnInfoEntity()//
                    .setColumnName("ds")//
                    .setColumnDesc("分区日期:格式yyyy-mm-dd")//
                    .setColumnType("STRING")),
    /**
     * StarRocks 默认字段：分区客户节点编码.
     */
    STARROCKS_DISTRIBUTED_CODE(DatabaseTypeEnum.STARROCKS.name().toLowerCase() + "_distributed_code", true, DatabaseTypeEnum.STARROCKS,
            () -> new ColumnInfoEntity()//
                    .setColumnName("distributed_code")//
                    .setColumnDesc("客户分区编码")//
                    .setColumnType("varchar")//
                    .setColumnLength(30)),
    /**
     * StarRocks 默认字段：分区时区字段.
     */
    STARROCKS_DISTZ(DatabaseTypeEnum.STARROCKS.name().toLowerCase() + "_distz", true, DatabaseTypeEnum.STARROCKS,
            () -> new ColumnInfoEntity()//
                    .setColumnName("distz")//
                    .setColumnDesc("时区:格式-12to12")//
                    .setColumnType("varchar")//
                    .setColumnLength(2)),
    /**
     * StarRocks 默认字段：分区日期字段.
     */
    STARROCKS_DS(DatabaseTypeEnum.STARROCKS.name().toLowerCase() + "_ds", true, DatabaseTypeEnum.STARROCKS,
            () -> new ColumnInfoEntity()//
                    .setColumnName("ds")//
                    .setColumnDesc("分区日期:格式yyyy-mm-dd")//
                    .setColumnType("date"));
    
    /**
     * 前端/后端交互使用的唯一键 (e.g., "maxcompute_ds").
     */
    private final String key;
    
    /**
     * 是否是分区字段.
     */
    private final boolean isPartition;
    
    /**
     * 所属的数据库类型.
     */
    private final DatabaseTypeEnum databaseType;
    
    /**
     * 对应的字段详细信息实体. 使用Supplier延迟加载，避免类加载时就创建所有实例。
     */
    private final Supplier<ColumnInfoEntity> columnInfoEntitySupplier;
    
    /**
     * 构造函数.
     *
     * @param key                      前端/后端交互使用的唯一键
     * @param isPartition              是否是分区字段
     * @param databaseType             所属的数据库类型
     * @param columnInfoEntitySupplier 对应的字段详细信息实体
     */
    DefaultColumnEnum(String key, boolean isPartition, DatabaseTypeEnum databaseType, Supplier<ColumnInfoEntity> columnInfoEntitySupplier) {
        this.key = key;
        this.isPartition = isPartition;
        this.databaseType = databaseType;
        this.columnInfoEntitySupplier = columnInfoEntitySupplier;
    }
    
    /**
     * 核心静态方法：根据数据库类型获取其所有默认字段的 KeyValueDto 列表.
     *
     * @param databaseType 数据库类型枚举
     * @return 对应的 KeyValueDto 列表
     */
    public static Map<String, ColumnInfoEntity> getDefaultColumnByDatabaseType(DatabaseTypeEnum databaseType) {
        return Arrays.stream(values()).filter(e -> e.getDatabaseType() == databaseType)
                .collect(Collectors.toMap(DefaultColumnEnum::getKey, e -> e.columnInfoEntitySupplier.get()));
    }
    
    /**
     * 根据字段 Key 获取字段详细信息实体.
     *
     * @param key 字段 Key
     * @return 字段详细信息实体
     */
    public static ColumnInfoEntity fromKeyForColumn(String key) {
        if (key == null) {
            return null;
        }
        return fromKey(key) == null ? null : fromKey(key).columnInfoEntitySupplier.get();
    }
    
    /**
     * 根据字段 Key 获取字段详细信息实体.
     *
     * @param key 字段 Key
     * @return 字段详细信息实体
     */
    public static DefaultColumnEnum fromKey(String key) {
        if (key == null) {
            return null;
        }
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst().orElse(null);
    }
}

package com.datafusion.manager.metadata.enums;

/**
 * StarRocks table_mode枚举.
 *
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
public enum StarrocksTableModel {

    /**
     * 明细模型.
     */
    DUPLICATE_TABLE("DUP_KEYS", "DUPLICATE KEY"),

    /**
     * 主键模型.
     */
    PRIMARY_TABLE("PRIMARY_KEYS", "PRIMARY KEY"),
    /**
     * 更新模型.
     */
    UNIQUE_TABLE("UNIQUE_KEYS", "UNIQUE KEY"),
    /**
     * 聚合模型.
     */
    AGGREGATE_TABLE("AGG_KEYS", "AGGREGATE KEY");

    /**
     * 数据库information存储的模型值.
     */
    private String modelType;

    /**
     * 数据库建表的声明方式.
     */
    private String createKey;

    /**
     * 私有化构造方法.
     */
    StarrocksTableModel(String modelType, String createKey) {
        this.createKey = createKey;
        this.modelType = modelType;
    }

    /**
     * .
     *
     * @return 返回modelType
     */
    public String getModelType() {
        return modelType;
    }

    /**
     * .
     *
     * @return 返回createKey
     */
    public String getCreateKey() {
        return createKey;
    }

    /**
     * .
     * @param modelType 根据存储的modelType,识别TableModel
     * @return 返回对应的StarrocksTableMode
     */
    public static StarrocksTableModel getByModelType(String modelType) {
        for (StarrocksTableModel value : values()) {
            if (value.getModelType().equals(modelType)) {
                return value;
            }
        }
        return null;
    }
}

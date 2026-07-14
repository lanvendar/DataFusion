package com.datafusion.manager.ingestion.enums;

/**
 * 数据集成-任务类型枚举.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
public enum IngestionTaskTypeEnum {

    /**
     * 实时同步.
     */
    DATAX("DATAX", "DATAX"),

    /**
     * 离线同步.
     *
     */
    OFFLINE_SYNC("SEATUNNEl", "SEATUNNEl");

    /**
     * 任务类型id.
     */
    String taskTypeId;

    /**
     * 任务类型描述.
     */
    String taskTypeDesc;

    /**
     * 构造函数.
     *
     * @param taskTypeId   任务类型id
     * @param taskTypeDesc 任务类型描述
     */
    IngestionTaskTypeEnum(String taskTypeId, String taskTypeDesc) {
        this.taskTypeId = taskTypeId;
        this.taskTypeDesc = taskTypeDesc;
    }

    /**
     * 获取任务类型id.
     *
     * @return 任务类型id
     */
    public String getTaskTypeId() {
        return taskTypeId;
    }

    /**
     * 获取任务类型描述.
     *
     * @return 任务类型描述
     */
    public String getTaskTypeDesc() {
        return taskTypeDesc;
    }

    /**
     * 根据taskTypeId获取描述.
     *
     * @param taskTypeId 任务类型id
     * @return 任务类型描述
     */
    public static String getDescByTypeId(String taskTypeId) {
        if (taskTypeId == null) {
            return null;
        }
        for (IngestionTaskTypeEnum entry : IngestionTaskTypeEnum.values()) {
            if (entry.getTaskTypeId().equalsIgnoreCase(taskTypeId)) {
                return entry.getTaskTypeDesc();
            }
        }
        return null;
    }
}

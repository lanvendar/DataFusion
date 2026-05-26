package com.datafusion.scheduler.master.example;

import lombok.Getter;

/**
 * 扩展 TaskLink DTO，增加 flowId 字段以支持 BeanTableConverter 按 flowId 查询.
 *
 * <p>TaskLink 本身继承 Link 仅有 id/startId/endId，
 * 而 TaskStorageMem.getTaskInfoLink 通过 flowId 列查询，因此需此 DTO 写入数据.
 */
@Getter
public class TaskLinkDto {

    private final String id;
    private final String startId;
    private final String endId;
    private final String flowId;

    public TaskLinkDto(String id, String startId, String endId, String flowId) {
        this.id = id;
        this.startId = startId;
        this.endId = endId;
        this.flowId = flowId;
    }
}

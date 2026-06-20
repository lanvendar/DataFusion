package com.datafusion.manager.scheduler.service;

import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedResultDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterResultDto;

/**
 * 任务定义统一登记Service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
public interface TaskDefinitionRegisterService {

    /**
     * 登记任务定义.
     *
     * @param dto 登记参数
     * @return 登记结果
     */
    TaskDefinitionRegisterResultDto register(TaskDefinitionRegisterDto dto);

    /**
     * 标记任务定义未同步.
     *
     * @param dto 标记参数
     * @return 标记结果
     */
    TaskDefinitionMarkUnsyncedResultDto markUnsynced(TaskDefinitionMarkUnsyncedDto dto);
}

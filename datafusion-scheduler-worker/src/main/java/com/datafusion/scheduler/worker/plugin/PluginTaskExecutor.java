package com.datafusion.scheduler.worker.plugin;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;

/**
 * 插件任务执行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface PluginTaskExecutor {

    /**
     * 获取插件类型.
     *
     * @return 插件类型
     */
    String pluginType();

    /**
     * 校验任务请求参数.
     *
     * @param request 任务请求
     */
    default void validateTaskRequest(TaskRequest request) {

    }

    /**
     * 提交任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult submitTask(TaskRequest request);

    /**
     * 停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult stopTask(TaskRequest request);

    /**
     * 强制停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult killTask(TaskRequest request);

    /**
     * 任务完成后的插件侧收尾动作.
     *
     * @param request 任务请求
     * @return 是否完成清理
     */
    boolean finishTask(TaskRequest request);

    /**
     * 销毁任务级执行资源.
     *
     * @param request 任务请求
     */
    default void destroyTask(TaskRequest request) {

    }
}

package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DataX 插件任务执行器公共入口.
 *
 * <p>公共类只解析固定快照参数并分发到运行模式实现；子类修改动作上下文中的候选状态，
 * 不读取或写入任务执行存储。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public abstract class DataxPluginTaskExecutor implements PluginTaskExecutor {

    /** 插件类型. */
    public static final String PLUGIN_TYPE = "DATAX";

    /** 参数解析器. */
    private final DataxParamResolver paramResolver;

    /**
     * 创建 DataX 插件执行器.
     *
     * @param paramResolver 参数解析器
     */
    protected DataxPluginTaskExecutor(DataxParamResolver paramResolver) {
        this.paramResolver = paramResolver;
    }

    @Override
    public final String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public final void validate(RunningTaskContext context) {
        resolve(context);
    }

    @Override
    public final WorkerResult submit(RunningTaskContext context) {
        return submit(context, resolve(context));
    }

    @Override
    public final WorkerResult stop(RunningTaskContext context) {
        return stop(context, resolve(context));
    }

    @Override
    public final WorkerResult kill(RunningTaskContext context) {
        return kill(context, resolve(context));
    }

    @Override
    public final boolean finish(RunningTaskContext context) {
        return context.getSnapshot().getPluginParam() == null || finish(context, resolve(context));
    }

    /**
     * 提交当前运行模式任务.
     *
     * @param context 动作上下文
     * @param param   DataX 参数
     * @return Worker 执行结果
     */
    protected abstract WorkerResult submit(RunningTaskContext context, DataxExecutionParam param);

    /**
     * 停止当前运行模式任务.
     *
     * @param context 动作上下文
     * @param param   DataX 参数
     * @return Worker 执行结果
     */
    protected abstract WorkerResult stop(RunningTaskContext context, DataxExecutionParam param);

    /**
     * 强杀当前运行模式任务.
     *
     * @param context 动作上下文
     * @param param   DataX 参数
     * @return Worker 执行结果
     */
    protected abstract WorkerResult kill(RunningTaskContext context, DataxExecutionParam param);

    /**
     * 清理当前运行模式任务.
     *
     * @param context 动作上下文
     * @param param   DataX 参数
     * @return 是否清理完成
     */
    protected boolean finish(RunningTaskContext context, DataxExecutionParam param) {
        return true;
    }

    /**
     * 更新动作候选状态.
     *
     * @param state       动作候选状态
     * @param status      目标状态
     * @param appId       运行引用
     * @param workDirPath 工作目录
     * @param result      结果 JSON
     */
    protected final void applyResult(WorkerTaskExecutionState state, StatusEnum status, String appId,
            String workDirPath, JsonNode result) {
        state.setStatus(status);
        state.setAppId(appId);
        state.setWorkDirPath(workDirPath);
        state.setResult(result);
    }

    /**
     * 将候选状态转换为既有 WorkerResult.
     *
     * @param state 动作候选状态
     * @return Worker 执行结果
     */
    protected final WorkerResult workerResult(WorkerTaskExecutionState state) {
        return WorkerResult.builder()
                .workerId(state.getWorkerId())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .message(resultText(state.getResult(), "message"))
                .pluginLogUri(resultText(state.getResult(), "pluginLogUri"))
                .outputVars(state.getOutputVars())
                .build();
    }

    /**
     * 读取结果 JSON 文本字段.
     *
     * @param result    结果 JSON
     * @param fieldName 字段名
     * @return 字段值
     */
    protected final String resultText(JsonNode result, String fieldName) {
        return result != null && result.hasNonNull(fieldName) ? result.get(fieldName).asText() : null;
    }

    /**
     * 解析指定快照的 DataX 参数.
     *
     * @param snapshot    任务提交快照
     * @param workDirPath 固定工作目录
     * @return DataX 参数
     */
    protected final DataxExecutionParam resolve(WorkerTaskExecutionSnap snapshot, String workDirPath) {
        return paramResolver.resolve(snapshot, workDirPath);
    }

    private DataxExecutionParam resolve(RunningTaskContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context不能为空");
        }
        return resolve(context.getSnapshot(), context.getWorkDirPath());
    }
}

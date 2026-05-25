package com.datafusion.manager.development.service.sql;

import com.datafusion.manager.development.dto.ExecSqlResultDto;

/**
 * 开发侧SQL执行事件回调.
 *
 * <p>由具体的 {@link DevSqlExecutor} 在执行过程中触发, 服务层据此更新任务状态/日志/结果.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
public interface SqlExecutionCallback {

    /**
     * 追加一行执行日志.
     *
     * @param level   日志级别
     * @param message 日志正文
     */
    void onLog(String level, String message);

    /**
     * 第 index 段SQL开始执行.
     *
     * @param index 索引, 从0开始
     */
    void onStatementStart(int index);

    /**
     * 第 index 段SQL拿到底层引擎实例ID.
     *
     * @param index      索引
     * @param instanceId 引擎实例ID
     */
    void onInstanceId(int index, String instanceId);

    /**
     * 第 index 段SQL执行成功.
     *
     * @param index        索引
     * @param costMs       耗时, 毫秒
     * @param resultIfAny  本段返回的结果集, 没有则为 null
     */
    void onStatementSuccess(int index, long costMs, ExecSqlResultDto resultIfAny);

    /**
     * 第 index 段SQL执行失败.
     *
     * @param index    索引
     * @param costMs   耗时, 毫秒
     * @param errorMsg 错误信息
     */
    void onStatementFailure(int index, long costMs, String errorMsg);

    /**
     * 第 index 段SQL因取消而终止.
     *
     * @param index  索引
     * @param costMs 耗时, 毫秒
     */
    void onStatementCancelled(int index, long costMs);
}

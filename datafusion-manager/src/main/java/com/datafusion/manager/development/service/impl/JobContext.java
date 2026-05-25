package com.datafusion.manager.development.service.impl;

import com.datafusion.manager.development.dto.ExecSqlLogLineDto;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.development.dto.ExecSqlStatementStatusDto;
import com.datafusion.manager.development.service.sql.DevSqlExecutor;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 开发侧SQL异步任务上下文.
 *
 * <p>所有可变字段统一通过外层 {@code synchronized (jobCtx)} 进行保护, 以保证 status 接口与 worker 线程之间的可见性.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
class JobContext {

    /**
     * 待执行状态.
     */
    static final String STATUS_PENDING = "PENDING";

    /**
     * 运行中状态.
     */
    static final String STATUS_RUNNING = "RUNNING";

    /**
     * 成功状态.
     */
    static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 失败状态.
     */
    static final String STATUS_FAILED = "FAILED";

    /**
     * 取消状态.
     */
    static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 任务ID.
     */
    private final UUID jobId;

    /**
     * 拥有者用户名.
     */
    private final String owner;

    /**
     * 数据源实体.
     */
    private final DataSourceInfoEntity dsEntity;

    /**
     * 选定的执行器.
     */
    private final DevSqlExecutor executor;

    /**
     * 取消信号.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 每段SQL状态.
     */
    private final List<ExecSqlStatementStatusDto> statements;

    /**
     * 日志增量队列.
     */
    private final CopyOnWriteArrayList<ExecSqlLogLineDto> logs = new CopyOnWriteArrayList<>();

    /**
     * 当前正在执行的 JDBC Statement 句柄(按语句索引).
     */
    private final Map<Integer, Statement> runningStatements = new ConcurrentHashMap<>();

    /**
     * 任务整体状态.
     */
    private volatile String status = STATUS_PENDING;

    /**
     * 任务级错误信息.
     */
    private volatile String errorMsg;

    /**
     * 最终结果.
     */
    private volatile ExecSqlResultDto finalResult;

    /**
     * 提交时间(毫秒).
     */
    private final long submittedAt;

    /**
     * 开始时间(毫秒).
     */
    private volatile Long startedAt;

    /**
     * 完成时间(毫秒).
     */
    private volatile Long finishedAt;

    /**
     * 构造任务上下文.
     *
     * @param jobId      任务ID
     * @param owner      拥有者
     * @param dsEntity   数据源
     * @param executor   执行器
     * @param statements 预先构建好的语句状态列表
     */
    JobContext(UUID jobId, String owner, DataSourceInfoEntity dsEntity,
               DevSqlExecutor executor, List<ExecSqlStatementStatusDto> statements) {
        this.jobId = jobId;
        this.owner = owner;
        this.dsEntity = dsEntity;
        this.executor = executor;
        this.statements = new CopyOnWriteArrayList<>(statements);
        this.submittedAt = System.currentTimeMillis();
    }

    UUID getJobId() {
        return jobId;
    }

    String getOwner() {
        return owner;
    }

    DataSourceInfoEntity getDsEntity() {
        return dsEntity;
    }

    DevSqlExecutor getExecutor() {
        return executor;
    }

    AtomicBoolean getCancelledFlag() {
        return cancelled;
    }

    List<ExecSqlStatementStatusDto> getStatements() {
        return statements;
    }

    CopyOnWriteArrayList<ExecSqlLogLineDto> getLogs() {
        return logs;
    }

    Map<Integer, Statement> getRunningStatements() {
        return runningStatements;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getErrorMsg() {
        return errorMsg;
    }

    void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    ExecSqlResultDto getFinalResult() {
        return finalResult;
    }

    void setFinalResult(ExecSqlResultDto finalResult) {
        this.finalResult = finalResult;
    }

    long getSubmittedAt() {
        return submittedAt;
    }

    Long getStartedAt() {
        return startedAt;
    }

    void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    Long getFinishedAt() {
        return finishedAt;
    }

    void setFinishedAt(Long finishedAt) {
        this.finishedAt = finishedAt;
    }

    /**
     * 是否处于终态.
     *
     * @return 是否终态
     */
    boolean isTerminal() {
        return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status);
    }
}

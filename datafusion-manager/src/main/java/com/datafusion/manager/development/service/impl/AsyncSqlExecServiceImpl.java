package com.datafusion.manager.development.service.impl;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.development.config.DevelopmentSqlConfig;
import com.datafusion.manager.development.dto.ExecSqlJobStatusDto;
import com.datafusion.manager.development.dto.ExecSqlLogLineDto;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.development.dto.ExecSqlStatementStatusDto;
import com.datafusion.manager.development.service.AsyncSqlExecService;
import com.datafusion.manager.development.service.sql.DevSqlExecutor;
import com.datafusion.manager.development.service.sql.SqlExecutionCallback;
import com.datafusion.manager.development.service.sql.SqlExecutorRouter;
import com.datafusion.manager.development.service.sql.SqlScriptUtils;
import com.datafusion.manager.development.service.sql.StatementRegistrar;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 开发侧SQL异步执行服务实现, 使用进程内 Map 维持任务状态.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Slf4j
@Service
public class AsyncSqlExecServiceImpl implements AsyncSqlExecService {

    /**
     * 任务终态完成多久后清理(毫秒).
     */
    private static final long JOB_TTL_MS = 30L * 60L * 1000L;

    /**
     * 数据源服务.
     */
    private final DataSourceInfoService dataSourceInfoService;

    /**
     * 执行器路由.
     */
    private final SqlExecutorRouter executorRouter;

    /**
     * 异步执行线程池.
     */
    private final Executor executor;

    /**
     * 任务上下文存储.
     */
    private final Map<UUID, JobContext> jobs = new ConcurrentHashMap<>();

    /**
     * 构造函数.
     *
     * @param dataSourceInfoService 数据源服务
     * @param executorRouter        执行器路由
     * @param executor              开发侧SQL线程池
     */
    public AsyncSqlExecServiceImpl(DataSourceInfoService dataSourceInfoService,
                                   SqlExecutorRouter executorRouter,
                                   @Qualifier(DevelopmentSqlConfig.DEV_SQL_EXECUTOR) Executor executor) {
        this.dataSourceInfoService = dataSourceInfoService;
        this.executorRouter = executorRouter;
        this.executor = executor;
    }

    @Override
    public UUID submit(ExecuteCreateTableDto dto) {
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(dto.getDatasourceId());
        SqlScriptUtils.checkSqlExecutable(dsEntity);
        List<String> sqls = SqlScriptUtils.splitAndClean(dto.getSql());

        UUID jobId = UUID.randomUUID();
        String owner = HttpUtils.getCurrentUserName();
        DevSqlExecutor sqlExecutor = executorRouter.route(dsEntity);

        List<ExecSqlStatementStatusDto> statements = new ArrayList<>(sqls.size());
        for (int i = 0; i < sqls.size(); i++) {
            ExecSqlStatementStatusDto stmt = new ExecSqlStatementStatusDto();
            stmt.setIndex(i);
            stmt.setSql(sqls.get(i));
            stmt.setStatus(JobContext.STATUS_PENDING);
            statements.add(stmt);
        }

        JobContext ctx = new JobContext(jobId, owner, dsEntity, sqlExecutor, statements);
        jobs.put(jobId, ctx);

        if (sqls.isEmpty()) {
            synchronized (ctx) {
                ctx.setStatus(JobContext.STATUS_SUCCESS);
                ctx.setStartedAt(System.currentTimeMillis());
                ctx.setFinishedAt(System.currentTimeMillis());
                appendLog(ctx, "WARN", "SQL脚本为空或仅包含注释");
            }
            return jobId;
        }

        executor.execute(() -> runJob(ctx));
        return jobId;
    }

    @Override
    public ExecSqlJobStatusDto getStatus(UUID jobId, int logOffset) {
        JobContext ctx = requireOwnedJob(jobId);
        return snapshot(ctx, Math.max(0, logOffset));
    }

    @Override
    public ExecSqlJobStatusDto cancel(UUID jobId) {
        JobContext ctx = requireOwnedJob(jobId);
        if (ctx.isTerminal()) {
            return snapshot(ctx, 0);
        }
        ctx.getCancelledFlag().set(true);
        appendLog(ctx, "WARN", "收到取消请求, 正在停止运行中的实例...");

        // 1) JDBC 场景: 尝试 cancel 当前正在执行的 Statement
        List<Statement> stmtsToCancel = new ArrayList<>();
        synchronized (ctx) {
            stmtsToCancel.addAll(ctx.getRunningStatements().values());
        }
        for (Statement stmt : stmtsToCancel) {
            try {
                stmt.cancel();
            } catch (Exception e) {
                log.warn("取消 JDBC Statement 失败 jobId={}, msg={}", jobId, e.getMessage());
            }
        }

        List<ExecSqlStatementStatusDto> running = new ArrayList<>();
        synchronized (ctx) {
            for (ExecSqlStatementStatusDto stmt : ctx.getStatements()) {
                if (JobContext.STATUS_RUNNING.equals(stmt.getStatus()) && stmt.getInstanceId() != null) {
                    running.add(stmt);
                }
            }
        }
        for (ExecSqlStatementStatusDto stmt : running) {
            try {
                ctx.getExecutor().stopInstance(ctx.getDsEntity(), stmt.getInstanceId());
            } catch (Exception e) {
                log.warn("停止实例失败 jobId={}, instanceId={}, msg={}", jobId, stmt.getInstanceId(), e.getMessage());
            }
        }
        return snapshot(ctx, 0);
    }

    /**
     * 后台清理任务: 终态超过 TTL 的任务从内存中移除.
     */
    @Scheduled(fixedDelay = 60_000L)
    public void cleanupExpiredJobs() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, JobContext>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, JobContext> entry = it.next();
            JobContext ctx = entry.getValue();
            if (!ctx.isTerminal()) {
                continue;
            }
            Long finishedAt = ctx.getFinishedAt();
            if (finishedAt != null && now - finishedAt > JOB_TTL_MS) {
                it.remove();
            }
        }
    }

    /**
     * 真正运行任务的 Runnable 主体.
     *
     * @param ctx 任务上下文
     */
    private void runJob(JobContext ctx) {
        synchronized (ctx) {
            ctx.setStartedAt(System.currentTimeMillis());
            ctx.setStatus(JobContext.STATUS_RUNNING);
        }
        appendLog(ctx, "INFO", String.format("任务开始, 数据源=%s, 共%d段SQL",
                ctx.getDsEntity().getDatabaseType(), ctx.getStatements().size()));
        SqlExecutionCallback callback = new JobCallback(ctx);
        try {
            ctx.getExecutor().execute(ctx.getDsEntity(),
                    statementsToSqls(ctx),
                    callback,
                    () -> ctx.getCancelledFlag().get());
            finalizeStatusAfterRun(ctx, null);
        } catch (Exception e) {
            log.error("开发侧SQL任务执行异常 jobId={}, msg={}", ctx.getJobId(), e.getMessage(), e);
            finalizeStatusAfterRun(ctx, e.getMessage());
        }
    }

    /**
     * 根据各段状态收敛任务终态.
     *
     * @param ctx     任务上下文
     * @param topMsg  外层异常信息(可空)
     */
    private void finalizeStatusAfterRun(JobContext ctx, String topMsg) {
        synchronized (ctx) {
            if (ctx.isTerminal()) {
                return;
            }
            boolean hasFailed = false;
            boolean hasCancelled = false;
            for (ExecSqlStatementStatusDto stmt : ctx.getStatements()) {
                if (JobContext.STATUS_FAILED.equals(stmt.getStatus())) {
                    hasFailed = true;
                } else if (JobContext.STATUS_CANCELLED.equals(stmt.getStatus())) {
                    hasCancelled = true;
                }
            }
            String finalStatus;
            if (hasFailed) {
                finalStatus = JobContext.STATUS_FAILED;
            } else if (hasCancelled || ctx.getCancelledFlag().get()) {
                finalStatus = JobContext.STATUS_CANCELLED;
            } else {
                finalStatus = JobContext.STATUS_SUCCESS;
            }
            ctx.setStatus(finalStatus);
            ctx.setFinishedAt(System.currentTimeMillis());
            if (topMsg != null && ctx.getErrorMsg() == null) {
                ctx.setErrorMsg(topMsg);
            }
            appendLog(ctx, JobContext.STATUS_FAILED.equals(finalStatus) ? "ERROR" : "INFO",
                    String.format("任务结束, 最终状态=%s", finalStatus));
        }
    }

    /**
     * 取出待执行的 SQL 文本列表, 顺序与 statements 一致.
     *
     * @param ctx 任务上下文
     * @return SQL 列表
     */
    private List<String> statementsToSqls(JobContext ctx) {
        List<String> list = new ArrayList<>(ctx.getStatements().size());
        for (ExecSqlStatementStatusDto stmt : ctx.getStatements()) {
            list.add(stmt.getSql());
        }
        return list;
    }

    /**
     * 校验任务存在且属于当前用户.
     *
     * @param jobId 任务ID
     * @return 任务上下文
     */
    private JobContext requireOwnedJob(UUID jobId) {
        if (jobId == null) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "jobId不能为空");
        }
        JobContext ctx = jobs.get(jobId);
        if (ctx == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务不存在或已过期: " + jobId);
        }
        String currentUser = HttpUtils.getCurrentUserName();
        if (currentUser != null && !currentUser.equals(ctx.getOwner())) {
            throw new CommonException(ErrorCodeEnum.USER_ERROR_A0312, "无权操作他人任务");
        }
        return ctx;
    }

    /**
     * 构建一次状态快照, 包含从 logOffset 起的日志增量.
     *
     * @param ctx       任务上下文
     * @param logOffset 日志偏移量
     * @return 状态快照
     */
    private ExecSqlJobStatusDto snapshot(JobContext ctx, int logOffset) {
        synchronized (ctx) {
            ExecSqlJobStatusDto dto = new ExecSqlJobStatusDto();
            dto.setJobId(ctx.getJobId());
            dto.setStatus(ctx.getStatus());
            dto.setErrorMsg(ctx.getErrorMsg());
            dto.setSubmittedAt(ctx.getSubmittedAt());
            dto.setStartedAt(ctx.getStartedAt());
            dto.setFinishedAt(ctx.getFinishedAt());
            dto.setFinalResult(ctx.isTerminal() ? ctx.getFinalResult() : null);

            List<ExecSqlStatementStatusDto> snapshot = new ArrayList<>(ctx.getStatements().size());
            for (ExecSqlStatementStatusDto src : ctx.getStatements()) {
                snapshot.add(copyStatement(src));
            }
            dto.setStatements(snapshot);

            int total = ctx.getLogs().size();
            int from = Math.min(logOffset, total);
            List<ExecSqlLogLineDto> incremental = from >= total
                    ? Collections.emptyList()
                    : new ArrayList<>(ctx.getLogs().subList(from, total));
            dto.setLogs(incremental);
            dto.setLogNextOffset(total);
            return dto;
        }
    }

    /**
     * 浅拷贝一段状态, 防止外部读取到中途修改的对象.
     *
     * @param src 源对象
     * @return 拷贝
     */
    private ExecSqlStatementStatusDto copyStatement(ExecSqlStatementStatusDto src) {
        ExecSqlStatementStatusDto dst = new ExecSqlStatementStatusDto();
        dst.setIndex(src.getIndex());
        dst.setSql(src.getSql());
        dst.setInstanceId(src.getInstanceId());
        dst.setStatus(src.getStatus());
        dst.setErrorMsg(src.getErrorMsg());
        dst.setCostMs(src.getCostMs());
        dst.setResult(src.getResult());
        return dst;
    }

    /**
     * 追加一行日志.
     *
     * @param ctx     任务上下文
     * @param level   日志级别
     * @param message 日志正文
     */
    private void appendLog(JobContext ctx, String level, String message) {
        ctx.getLogs().add(new ExecSqlLogLineDto(System.currentTimeMillis(), level, message));
    }

    /**
     * 包装为 SqlExecutionCallback, 作为 worker 与 JobContext 之间的事件桥.
     */
    private final class JobCallback implements SqlExecutionCallback, StatementRegistrar {

        /**
         * 关联的任务上下文.
         */
        private final JobContext ctx;

        /**
         * 构造回调.
         *
         * @param ctx 任务上下文
         */
        private JobCallback(JobContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onLog(String level, String message) {
            appendLog(ctx, level, message);
        }

        @Override
        public void onStatementStart(int index) {
            synchronized (ctx) {
                ExecSqlStatementStatusDto stmt = ctx.getStatements().get(index);
                stmt.setStatus(JobContext.STATUS_RUNNING);
            }
        }

        @Override
        public void onInstanceId(int index, String instanceId) {
            synchronized (ctx) {
                ExecSqlStatementStatusDto stmt = ctx.getStatements().get(index);
                stmt.setInstanceId(instanceId);
            }
        }

        @Override
        public void onStatementSuccess(int index, long costMs, ExecSqlResultDto resultIfAny) {
            synchronized (ctx) {
                ExecSqlStatementStatusDto stmt = ctx.getStatements().get(index);
                stmt.setStatus(JobContext.STATUS_SUCCESS);
                stmt.setCostMs(costMs);
                stmt.setResult(resultIfAny);
                if (resultIfAny != null) {
                    ctx.setFinalResult(resultIfAny);
                }
            }
        }

        @Override
        public void onStatementFailure(int index, long costMs, String errorMsg) {
            synchronized (ctx) {
                ExecSqlStatementStatusDto stmt = ctx.getStatements().get(index);
                stmt.setStatus(JobContext.STATUS_FAILED);
                stmt.setCostMs(costMs);
                stmt.setErrorMsg(errorMsg);
                if (ctx.getErrorMsg() == null) {
                    ctx.setErrorMsg(String.format("第%d段SQL失败: %s", index + 1, errorMsg));
                }
            }
        }

        @Override
        public void onStatementCancelled(int index, long costMs) {
            synchronized (ctx) {
                ExecSqlStatementStatusDto stmt = ctx.getStatements().get(index);
                stmt.setStatus(JobContext.STATUS_CANCELLED);
                stmt.setCostMs(costMs);
            }
        }

        @Override
        public void register(int index, Statement stmt) {
            if (stmt == null) {
                return;
            }
            ctx.getRunningStatements().put(index, stmt);
        }

        @Override
        public void unregister(int index) {
            ctx.getRunningStatements().remove(index);
        }
    }
}

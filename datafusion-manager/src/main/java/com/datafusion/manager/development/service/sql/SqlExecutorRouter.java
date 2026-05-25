package com.datafusion.manager.development.service.sql;

import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 开发侧SQL执行器路由.
 *
 * <p>按数据源类型选择具体执行器, MaxCompute 走 {@link MaxcomputeSqlExecutor}, 其它走 {@link FallbackSqlExecutor}.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Component
public class SqlExecutorRouter {

    private final List<DevSqlExecutor> executors;

    public SqlExecutorRouter(List<DevSqlExecutor> executors) {
        this.executors = executors;
    }

    /**
     * 选择数据源对应的执行器.
     *
     * @param dsEntity 数据源
     * @return 执行器
     */
    public DevSqlExecutor route(DataSourceInfoEntity dsEntity) {
        // 1. 先找那些“明确支持”该类型的执行器（排除掉 fallback）
        Optional<DevSqlExecutor> specificExecutor = executors.stream()
                .filter(e -> !(e instanceof FallbackSqlExecutor)) // 排除兜底类
                .filter(e -> e.supports(dsEntity))
                .findFirst();

        return specificExecutor.orElseGet(() -> executors.stream()
                .filter(e -> e instanceof FallbackSqlExecutor)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到匹配的 SQL 执行器")));

    }
}

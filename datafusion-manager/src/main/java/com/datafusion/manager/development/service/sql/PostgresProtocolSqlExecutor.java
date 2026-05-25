package com.datafusion.manager.development.service.sql;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.TransformSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Postgres 协议数据源（Postgres/Hologres）的开发侧SQL执行器.
 *
 * <p>基于 JDBC 顺序执行多段 SQL, 通过 {@link Statement#cancel()} 支持取消.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/8
 * @since 2026/5/8
 */
@Slf4j
@Component
public class PostgresProtocolSqlExecutor implements DevSqlExecutor {

    /**
     * 单段SQL最多保留的预览行数.
     */
    private static final int MAX_PREVIEW_ROWS = 1000;

    @Override
    public boolean supports(DataSourceInfoEntity dsEntity) {
        return DatabaseTypeEnum.POSTGRES.getType().equals(dsEntity.getDatabaseType())
                || DatabaseTypeEnum.HOLOGRES.getType().equals(dsEntity.getDatabaseType());
    }

    @Override
    public void execute(DataSourceInfoEntity dsEntity,
                        List<String> sqls,
                        SqlExecutionCallback callback,
                        BooleanSupplier cancelled) {
        if (CollectionUtil.isEmpty(sqls)) {
            return;
        }
        TransformSupport transform = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo info = transform.transformDataSourceInfo(dsEntity);
        String statementIdPrefix = UUID.randomUUID().toString();

        try {
            Class.forName(info.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found: " + info.getDriverClass(), e);
        }

        try (Connection conn = DriverManager.getConnection(info.getJdbcUrl(), info.getUsername(), info.getPassword())) {
            for (int i = 0; i < sqls.size(); i++) {
                if (cancelled.getAsBoolean()) {
                    callback.onStatementCancelled(i, 0L);
                    continue;
                }
                String sql = sqls.get(i);
                if (StrUtil.isBlank(sql)) {
                    callback.onStatementSuccess(i, 0L, null);
                    continue;
                }
                executeSingle(conn, i, sql, statementIdPrefix, callback, cancelled);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Postgres 协议数据源执行SQL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行单段 SQL.
     */
    private void executeSingle(Connection conn,
                               int index,
                               String sql,
                               String statementIdPrefix,
                               SqlExecutionCallback callback,
                               BooleanSupplier cancelled) throws SQLException {
        long startMs = System.currentTimeMillis();
        callback.onStatementStart(index);
        String statementId = statementIdPrefix + "-" + (index + 1);
        callback.onInstanceId(index, statementId);
        callback.onLog("INFO", String.format("第%d段SQL开始执行(statementId=%s): %s", index + 1, statementId, abbreviate(sql)));

        try (Statement stmt = conn.createStatement()) {
            if (callback instanceof StatementRegistrar) {
                ((StatementRegistrar) callback).register(index, stmt);
            }
            boolean hasResultSet = stmt.execute(sql);
            long cost = System.currentTimeMillis() - startMs;

            if (cancelled.getAsBoolean()) {
                callback.onLog("WARN", String.format("第%d段SQL已取消(statementId=%s)", index + 1, statementId));
                callback.onStatementCancelled(index, cost);
                return;
            }

            ExecSqlResultDto result = null;
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    result = toResult(rs);
                }
            }
            callback.onLog("INFO", String.format("第%d段SQL执行完成(statementId=%s), 耗时%dms", index + 1, statementId, cost));
            callback.onStatementSuccess(index, cost, result);
        } catch (SQLException e) {
            long cost = System.currentTimeMillis() - startMs;
            if (cancelled.getAsBoolean()) {
                callback.onLog("WARN", String.format("第%d段SQL已取消(statementId=%s)", index + 1, statementId));
                callback.onStatementCancelled(index, cost);
                return;
            }
            callback.onLog("ERROR", String.format("第%d段SQL执行失败(statementId=%s): %s", index + 1, statementId, e.getMessage()));
            callback.onStatementFailure(index, cost, e.getMessage());
            throw e;
        } finally {
            if (callback instanceof StatementRegistrar) {
                ((StatementRegistrar) callback).unregister(index);
            }
        }
    }

    /**
     * 把 JDBC ResultSet 映射为通用结果.
     *
     * @param rs ResultSet
     * @return 结果
     */
    private ExecSqlResultDto toResult(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int read = 0;
        boolean truncated = false;
        while (rs.next()) {
            read++;
            if (rows.size() < MAX_PREVIEW_ROWS) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 1; c <= colCount; c++) {
                    row.put(columns.get(c - 1), rs.getObject(c));
                }
                rows.add(row);
            } else {
                truncated = true;
            }
        }

        ExecSqlResultDto dto = new ExecSqlResultDto();
        dto.setColumns(columns);
        dto.setRows(rows);
        dto.setRowCount(read);
        if (truncated) {
            dto.setMessage(String.format("执行成功, 已截断展示前%d行(共%d行)", MAX_PREVIEW_ROWS, read));
        } else {
            dto.setMessage("执行成功");
        }
        return dto;
    }

    /**
     * 截断长SQL用于日志.
     */
    private String abbreviate(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}


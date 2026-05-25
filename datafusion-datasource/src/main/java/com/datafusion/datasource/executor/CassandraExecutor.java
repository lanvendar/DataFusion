package com.datafusion.datasource.executor;

import com.datafusion.common.enums.CommandType;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.template.SqlParamRender;
import com.datafusion.datasource.model.ExecuteParam;
import com.datafusion.datasource.resultset.CassandraResultSetResolver;
import com.datafusion.datasource.resultset.ReturnType;
import com.datafusion.datasource.resultset.ReturnTypeParser;
import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cassandra 数据库执行器.
 * TODO 数据库条件具备下需测试.
 *
 * @author lanvendar
 * @version 2.0.0, 2025/07/28
 * @since 2022/4/25
 */
@Slf4j
public class CassandraExecutor implements Executor<Session> {
    
    /**
     * 结果集解析器.
     */
    private final CassandraResultSetResolver resolver;
    
    /**
     * 构造函数.
     *
     * @param resolver 用于将 Cassandra ResultSet 映射为 Java 对象的解析器.
     */
    public CassandraExecutor(CassandraResultSetResolver resolver) {
        this.resolver = resolver;
    }
    
    @Override
    public Object execute(Session conn, ExecuteParam executeParam) {
        // 顶层异常处理，与 JdbcExecutor 类似
        try {
            // Cassandra 的 BatchStatement 主要用于原子性的 DML 操作，而不是执行 DDL 脚本。
            // 我们将 `executeParam.isBatch()` 视为执行 DML 批处理的请求。
            if (executeParam.isBatch()) {
                int[] rawResult = executeScriptBatch(conn, executeParam.getRenders());
                return convertResult(rawResult, executeParam.getReturnType());
            } else {
                SqlParamRender render = executeParam.getRender();
                if (render == null) {
                    throw new CommonException("执行计划无效: 单语句执行模式下 render 对象不能为空.");
                }
                
                // Cassandra 驱动总是使用预编译语句。
                // 我们需要区分：是对同一条语句执行批量操作，还是执行单次操作。
                if (Boolean.TRUE.equals(render.isParamsBatch())) {
                    Object rawResult = executePreparedStatementBatch(conn, render);
                    return convertResult(rawResult, executeParam.getReturnType());
                } else {
                    return executePreparedStatement(conn, render, executeParam.getReturnType());
                }
            }
        } catch (DriverException e) {
            log.error("Cassandra 数据库操作失败", e);
            throw new CommonException("Cassandra 执行时发生错误: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行单条预编译语句.
     *
     * @param conn       Cassandra Session
     * @param render     渲染后的 SQL
     * @param returnType 返回类型
     * @return 查询结果
     */
    private Object executePreparedStatement(Session conn, SqlParamRender render, Type returnType) {
        PreparedStatement ps = conn.prepare(render.getSql());
        BoundStatement bs = ps.bind();
        
        bindParameters(bs, render.getParamsForSingle().orElse(Collections.emptyList()));
        
        ResultSet rs = conn.execute(bs);
        
        if (render.getCommandType().getCategory() == CommandType.Category.DQL) {
            return resolver.getResultSet(rs, returnType);
        } else {
            // 对于 DML/DDL，ResultSet 表示成功，但不包含数据行。
            return convertResult(rs, returnType);
        }
    }
    
    /**
     * 使用一条预编译语句和多组参数，执行批量 DML 操作.
     *
     * @param conn   Cassandra Session
     * @param render 渲染后的 SQL
     * @return 批量更新计数
     */
    private int[] executePreparedStatementBatch(Session conn, SqlParamRender render) {
        validateBatchOperation(render.getCommandType());
        
        PreparedStatement ps = conn.prepare(render.getSql());
        // LOGGED 批处理保证原子性，但有性能开销。UNLOGGED 用于大数据量更新。
        BatchStatement batch = new BatchStatement(BatchStatement.Type.LOGGED);
        
        List<List<Object>> batchParams = render.getParamsForBatch().orElse(Collections.emptyList());
        for (List<Object> params : batchParams) {
            batch.add(bindParameters(ps.bind(), params));
        }
        
        conn.execute(batch);
        
        // Cassandra 批量执行不返回每条语句的更新计数。
        // 我们返回一个由 SUCCESS_NO_INFO 组成的数组，表示操作成功但计数未知，
        // 这与 JDBC 中某些驱动的行为一致。
        int[] results = new int[batchParams.size()];
        Arrays.fill(results, Statement.SUCCESS_NO_INFO); // -2 表示成功，计数未知
        return results;
    }
    
    /**
     * 在一个批处理中执行多条可能不同的 DML 语句脚本.
     *
     * @param conn    Cassandra Session
     * @param renders 渲染后的 SQL
     * @return 批量更新计数
     */
    private int[] executeScriptBatch(Session conn, List<SqlParamRender> renders) {
        BatchStatement batch = new BatchStatement(BatchStatement.Type.LOGGED);
        for (SqlParamRender render : renders) {
            validateBatchOperation(render.getCommandType());
            PreparedStatement ps = conn.prepare(render.getSql());
            batch.add(bindParameters(ps.bind(), render.getParamsForSingle().orElse(Collections.emptyList())));
        }
        conn.execute(batch);
        
        int[] results = new int[renders.size()];
        Arrays.fill(results, Statement.SUCCESS_NO_INFO);
        return results;
    }
    
    /**
     * 将参数绑定到 Cassandra 的 BoundStatement.
     *
     * @param bs     cassandra 批量对象
     * @param params 参数列表
     * @return cassandra 批量对象
     */
    private BoundStatement bindParameters(BoundStatement bs, List<Object> params) {
        if (!params.isEmpty()) {
            // 新版驱动 API 支持直接绑定一个可变参数数组。
            bs.bind(params.toArray());
        }
        return bs;
    }
    
    /**
     * 将 Cassandra DML/DDL 操作的原始结果转换为声明的 Java 返回类型.
     *
     * @param rawResult  原始结果
     * @param targetType 声明的返回类型
     * @return 转换后的结果
     */
    private Object convertResult(Object rawResult, Type targetType) {
        final ReturnType rt = ReturnTypeParser.parseType(targetType);
        if (rt == null) {
            throw new CommonException("无法解析返回类型: " + targetType.getTypeName());
        }
        final Class<?> rawClass = rt.getRawClass();
        
        if (rawClass == void.class || rawClass == Void.class) {
            return null;
        }
        
        // 对于布尔类型，只要执行成功（没有抛出异常）就意味着 true。
        if (rawClass == boolean.class || rawClass == Boolean.class) {
            return true;
        }
        
        if (rawResult instanceof int[] && rawClass == int[].class) {
            return rawResult;
        }
        
        // Cassandra 不返回更新计数。如果期望返回 int，我们返回 1 表示“一个操作成功了”。
        if (rawResult instanceof ResultSet && (rawClass == int.class || rawClass == Integer.class)) {
            return 1;
        }
        
        throw new CommonException(
                "类型转换失败: Cassandra DML/DDL 操作的返回类型只能是 " + "int, int[], boolean 或 void.不支持转换为: " + targetType.getTypeName());
    }
    
    /**
     * 验证命令是适合批处理的 DML 类型.
     *
     * @param commandType 命令类型
     */
    private void validateBatchOperation(CommandType commandType) {
        if (commandType.getCategory() != CommandType.Category.DML) {
            throw new UnsupportedOperationException("Cassandra 批处理操作仅支持 DML 语句。当前类型: " + commandType);
        }
    }
}
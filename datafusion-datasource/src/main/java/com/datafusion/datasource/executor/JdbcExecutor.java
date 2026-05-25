package com.datafusion.datasource.executor;

import com.datafusion.common.enums.CommandType;
import com.datafusion.common.enums.JdbcExecutionType;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.template.SqlParamRender;
import com.datafusion.datasource.model.ExecuteParam;
import com.datafusion.datasource.resultset.JdbcResultSetResolver;
import com.datafusion.datasource.resultset.ReturnType;
import com.datafusion.datasource.resultset.ReturnTypeParser;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JDBC 执行器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
@Slf4j
public class JdbcExecutor implements Executor<Connection> {
    /**
     * jdbc 结果集解析器.
     */
    private final JdbcResultSetResolver resolver;
    
    /**
     * 构造函数.
     *
     * @param resolver 结果集解析器
     */
    public JdbcExecutor(JdbcResultSetResolver resolver) {
        this.resolver = resolver;
    }
    
    @Override
    public Object execute(Connection conn, ExecuteParam executeParam) {
        //此方法作为最外层,统一处理SQLException,避免在每个子方法中重复try-catch
        try {
            if (executeParam.isBatch()) {
                // 模式 1: 多语句脚本执行
                int[] rawResult = executeScript(conn, executeParam.getRenders());
                return convertResult(rawResult, executeParam.getReturnType());
            } else {
                // 模式 2: 单语句执行
                SqlParamRender render = executeParam.getRender();
                if (render == null) {
                    throw new CommonException("执行计划无效: 单语句执行模式下 render 对象不能为空.");
                }
                switch (render.getExecutionType()) {
                    case STATEMENT:
                        return executeStatement(conn, render, executeParam.getReturnType());
                    case PREPARED:
                        return executePreparedStatement(conn, render, executeParam.getReturnType());
                    case CALLABLE:
                        // Callable的实现可以遵循类似的健壮性原则
                        return executeCallable(conn, render, executeParam.getReturnType());
                    default:
                        throw new CommonException("执行计划无效: 不支持的 JDBC 执行类型 " + render.getExecutionType());
                }
            }
        } catch (SQLException e) {
            log.error("数据库操作失败", e);
            throw new CommonException("数据库执行时发生错误: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行 Statement 操作.
     *
     * @param conn       数据库连接
     * @param render     脚本渲染器
     * @param returnType 返回结果类型
     * @return 映射结果
     */
    private Object executeStatement(Connection conn, SqlParamRender render, Type returnType) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (render.getCommandType().getCategory() == CommandType.Category.DQL) {
                try (ResultSet rs = stmt.executeQuery(render.getSql())) {
                    return resolver.getResultSet(rs, returnType);
                }
            } else {
                Object rawResult = stmt.executeUpdate(render.getSql());
                return convertResult(rawResult, returnType);
            }
        }
    }
    
    /**
     * 执行 PreparedStatement 操作.
     *
     * @param conn       数据库连接
     * @param render     脚本渲染器
     * @param returnType 返回结果类型
     * @return 映射结果
     */
    private Object executePreparedStatement(Connection conn, SqlParamRender render, Type returnType) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(render.getSql())) {
            if (render.getCommandType().getCategory() == CommandType.Category.DQL) {
                bindParameters(ps, render.getParamsForSingle().orElse(Collections.emptyList()));
                try (ResultSet rs = ps.executeQuery()) {
                    return resolver.getResultSet(rs, returnType);
                }
            } else {
                Object rawResult;
                if (Boolean.TRUE.equals(render.isParamsBatch())) {
                    validateBatchOperation(render.getCommandType());
                    rawResult = executePreparedStatementBatch(ps, render.getParamsForBatch().orElse(Collections.emptyList()));
                } else {
                    bindParameters(ps, render.getParamsForSingle().orElse(Collections.emptyList()));
                    rawResult = ps.executeUpdate();
                }
                return convertResult(rawResult, returnType);
            }
        }
    }
    
    /**
     * 执行 PreparedStatement 批量操作.
     *
     * @param ps          PreparedStatement 对象
     * @param batchParams 批量参数列表
     * @return 批量操作结果 (int[])
     * @throws SQLException 数据库操作异常
     */
    private int[] executePreparedStatementBatch(PreparedStatement ps, List<List<Object>> batchParams) throws SQLException {
        if (batchSupported(ps.getConnection())) {
            try {
                log.trace("驱动支持批量更新，尝试使用 executeBatch()。");
                for (List<Object> params : batchParams) {
                    bindParameters(ps, params);
                    ps.addBatch();
                }
                return ps.executeBatch();
            } catch (SQLException e) {
                log.warn("驱动报告支持批量更新，但执行时仍然失败。正在清理批处理并回退到逐条执行模式。", e);
                try {
                    ps.clearBatch();
                } catch (SQLException ignore) {
                
                }
                // 回退到迭代执行
                return fallbackToExecuteUpdate(ps, batchParams);
            }
        } else {
            // 迭代执行
            return fallbackToExecuteUpdate(ps, batchParams);
        }
    }
    
    /**
     * 检查数据库连接是否支持批量更新.
     *
     * @param conn 数据库连接
     * @return 是否支持批量更新
     */
    private boolean batchSupported(Connection conn) {
        try {
            return conn.getMetaData().supportsBatchUpdates();
        } catch (SQLException e) {
            log.warn("无法通过元数据检查批量更新支持情况，将保守地认为不支持。错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 循环执行 PreparedStatement 批量操作.
     *
     * @param ps          PreparedStatement 对象
     * @param batchParams 批量参数列表
     * @return 批量操作结果 (int[])
     * @throws SQLException 数据库操作异常
     */
    private int[] fallbackToExecuteUpdate(PreparedStatement ps, List<List<Object>> batchParams) throws SQLException {
        log.warn("JDBC驱动不支持 executeBatch(),回退到逐条执行模式,这可能会影响性能!!!");
        
        int[] results = new int[batchParams.size()];
        for (int i = 0; i < batchParams.size(); i++) {
            ps.clearParameters();
            bindParameters(ps, batchParams.get(i));
            results[i] = ps.executeUpdate();
        }
        return results;
    }
    
    /**
     * 执行多语句脚本,智能选择事务或非事务模式.
     *
     * @param conn    数据库连接
     * @param renders SQL参数渲染列表
     * @return 脚本执行结果
     * @throws SQLException 数据库操作异常
     */
    private int[] executeScript(Connection conn, List<SqlParamRender> renders) throws SQLException {
        if (transactionSupported(conn)) {
            return executeScriptWithTransaction(conn, renders);
        } else {
            return executeScriptWithoutTransaction(conn, renders);
        }
    }
    
    /**
     * 检查数据库连接是否支持事务,通过尝试关闭自动提交来判断.
     *
     * @param conn 数据库连接
     * @return 是否支持事务
     */
    private boolean transactionSupported(Connection conn) {
        boolean originalAutoCommit;
        try {
            originalAutoCommit = conn.getAutoCommit();
            // 如果已经是手动提交模式，那肯定支持事务
            if (!originalAutoCommit) {
                return true;
            }
            // 尝试切换模式
            conn.setAutoCommit(false);
            // 成功切换后，立即恢复原状，避免影响后续操作
            conn.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            // 任何异常都意味着不支持
            return false;
        }
    }
    
    /**
     * 执行策略: 在事务中,以原子性的批处理方式执行脚本.
     *
     * @param conn    数据库连接
     * @param renders SQL参数渲染列表
     * @return 脚本执行结果
     * @throws SQLException 数据库操作异常
     */
    private int[] executeScriptWithTransaction(Connection conn, List<SqlParamRender> renders) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit(); // 保存原始状态
        try {
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }
            
            try (Statement stmt = conn.createStatement()) {
                for (SqlParamRender render : renders) {
                    validateScriptStatement(render);
                    stmt.addBatch(render.getSql());
                }
                int[] results = stmt.executeBatch();
                conn.commit();
                return results;
            }
        } catch (SQLException e) {
            // 捕获到任何异常后，尝试回滚
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                log.error("事务回滚失败", rollbackEx);
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            // 确保恢复连接的原始状态
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException finalEx) {
                log.error("恢复连接的自动提交状态失败", finalEx);
            }
        }
    }
    
    /**
     * 执行策略:非事务,逐条执行脚本.
     *
     * @param conn    数据库连接
     * @param renders 渲染后的SQL参数
     * @return 执行结果
     * @throws SQLException 数据库操作异常
     */
    private int[] executeScriptWithoutTransaction(Connection conn, List<SqlParamRender> renders) throws SQLException {
        log.warn("JDBC驱动不支持事务,脚本将以自动提交模式逐条执行. 注意:此模式下脚本执行非原子性!");
        List<Integer> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            for (SqlParamRender render : renders) {
                validateScriptStatement(render);
                results.add(stmt.executeUpdate(render.getSql()));
            }
        }
        return results.stream().mapToInt(Integer::intValue).toArray();
    }
    
    /**
     * 将JDBC返回的非 DQL 类型的结果.
     * 如 {@code int, int[], boolean}转换为用户声明的返回类型{@code Void, Integer, int, int[], boolean, Boolean}.
     *
     * @param rawResult  原始结果
     * @param targetType 用户声明的返回类型
     * @return 转换后的结果
     */
    private Object convertResult(Object rawResult, Type targetType) {
        final ReturnType rt = ReturnTypeParser.parseType(targetType);
        if (rt == null) {
            throw new CommonException("无法解析返回类型: " + targetType.getTypeName());
        }
        final Class<?> rawClass = rt.getRawClass();
        
        // 1. 目标类型是 void
        if (rawClass == void.class || rawClass == Void.class) {
            return null;
        }
        
        // 2. 目标类型是 boolean 或 Boolean
        if (rawClass == boolean.class || rawClass == Boolean.class) {
            if (rawResult instanceof Integer) {
                // 单条更新：影响行数 > 0 则为 true
                return (Integer) rawResult > 0;
            }
            if (rawResult instanceof int[]) {
                // 批量更新：所有语句都成功 (>=0) 则为 true
                // Statement.SUCCESS_NO_INFO (-2) 也算成功
                return Arrays.stream((int[]) rawResult)
                        .allMatch(r -> r >= 0 || r == Statement.SUCCESS_NO_INFO);
            }
        }
        
        // 3. 目标类型与原始类型匹配，直接返回
        if (rawResult instanceof Integer && (rawClass == int.class || rawClass == Integer.class)) {
            return rawResult;
        }
        if (rawResult instanceof int[] && rawClass == int[].class) {
            return rawResult;
        }
        
        // 如果无法匹配上述任何一种情况，说明用户声明了不支持的返回类型
        throw new CommonException("类型转换失败: DML/DDL/DCL 操作的返回类型只能是 "
                + "int, int[], boolean 或 void.不支持转换为: " + targetType.getTypeName());
    }
    
    /**
     * 绑定参数.
     *
     * @param ps     PreparedStatement
     * @param params 参数列表
     */
    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
    
    /**
     * 验证批处理操作.
     *
     * @param commandType 命令类型
     */
    private void validateBatchOperation(CommandType commandType) {
        if (commandType.getCategory() != CommandType.Category.DML) {
            throw new UnsupportedOperationException("JDBC 批处理操作仅支持 DML 语句。当前类型: " + commandType);
        }
    }
    
    /**
     * 验证脚本语句.
     *
     * @param render 渲染后的SQL
     */
    private void validateScriptStatement(SqlParamRender render) {
        if (render.getExecutionType() != JdbcExecutionType.STATEMENT) {
            throw new CommonException("多语句脚本模式下只支持 STATEMENT 类型的SQL，发现类型: " + render.getExecutionType());
        }
        CommandType.Category category = render.getCommandType().getCategory();
        if (category != CommandType.Category.DDL && category != CommandType.Category.DCL) {
            log.warn("脚本中包含非DDL/DCL语句，这可能不是预期行为: {}", render.getSql());
        }
    }
    
    /**
     * 执行存储过程 (待实现).
     *
     * @param conn       数据库连接
     * @param render     脚本渲染器
     * @param returnType 返回结果类型
     * @return 存储过程执行结果
     */
    private Object executeCallable(Connection conn, SqlParamRender render, Type returnType) throws SQLException {
        //TODO 实现存储过程的完整调用逻辑，包括注册出参、处理多结果集等(未测试!!!)
        log.warn("CallableStatement execution is not yet fully implemented.");
        
        try (CallableStatement cs = conn.prepareCall(render.getSql())) {
            bindParameters(cs, render.getParamsForSingle().orElse(Collections.emptyList()));
            // 使用 execute() 因为存储过程的结果未知
            boolean isResultSet = cs.execute();
            
            if (isResultSet) {
                try (ResultSet rs = cs.getResultSet()) {
                    return resolver.getResultSet(rs, returnType);
                }
            } else {
                return cs.getUpdateCount(); // 返回第一个更新计数
            }
        }
    }
}

package com.datafusion.common.template;

import com.datafusion.common.enums.CommandType;
import com.datafusion.common.enums.JdbcExecutionType;
import com.datafusion.common.enums.RenderType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

/**
 * sql模板渲染的参数抽象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/6/23
 * @since 2025/6/23
 */
@Setter
public class SqlParamRender {
    /**
     * 渲染参数类型.
     */
    @Getter
    private RenderType renderType;
    
    /**
     * sql命令类型.
     */
    @Getter
    private CommandType commandType;
    
    /**
     * sql执行类型.
     */
    @Getter
    private JdbcExecutionType executionType;
    
    /**
     * sql模板.
     */
    @Getter
    private String sql;
    
    /**
     * sql参数,统一使用Object以容纳单次和批量两种结构.
     * - 单条参数: {@code List<Object>}
     * - 多条参数: {@code List<List<Object>>}
     */
    private Object params;
    
    /**
     * 渲染出该条 SQL 的原始参数，用于调试和日志.
     * - 单条参数: {@code Map<String, Object>}
     * - 多条参数: {@code List<Map<String, Object>>}
     * transient: 此字段仅用于运行时调试，不需要被持久化或网络传输。
     */
    @Getter
    private transient Object originalParams;
    
    /**
     * 判断是否为批量操作.
     *
     * @return true: 批量, false: 单条, null: 无参数
     */
    public Boolean isParamsBatch() {
        if (params instanceof List) {
            List<?> list = (List<?>) params;
            return !list.isEmpty() && list.get(0) instanceof List;
        }
        return false;
    }
    
    /**
     * 单条参数列表.
     *
     * @return 参数列表
     */
    public Optional<List<Object>> getParamsForSingle() {
        if (params instanceof List && !isParamsBatch()) {
            return Optional.of((List<Object>) params);
        }
        return Optional.empty();
    }
    
    /**
     * 多条参数列表.
     *
     * @return 参数列表
     */
    public Optional<List<List<Object>>> getParamsForBatch() {
        if (isParamsBatch()) {
            return Optional.of((List<List<Object>>) params);
        }
        return Optional.empty();
    }
}

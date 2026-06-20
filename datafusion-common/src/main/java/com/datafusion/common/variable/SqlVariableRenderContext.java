package com.datafusion.common.variable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SQL 变量渲染上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SqlVariableRenderContext extends VariableRenderContext {

    /**
     * SQL 模板时间.
     */
    private Long templateSqlTime;

    @Override
    public Long getDefaultTimeMillis() {
        return templateSqlTime;
    }
}

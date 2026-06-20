package com.datafusion.common.variable;

import com.datafusion.scheduler.model.Variable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 变量渲染上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class VariableRenderContext {

    /**
     * 变量映射.
     */
    private Map<String, Variable> variables;

    /**
     * 获取变量值.
     *
     * @param name 变量名
     * @return 变量值
     */
    public String getVariableValue(String name) {
        if (name == null || variables == null) {
            return null;
        }
        Variable variable = variables.get(name);
        return variable == null ? null : variable.getValue();
    }

    /**
     * 获取默认时间戳.
     *
     * @return 默认时间戳
     */
    public Long getDefaultTimeMillis() {
        return null;
    }
}

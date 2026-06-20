package com.datafusion.common.variable;

import com.datafusion.scheduler.model.Variable;

/**
 * 变量工具.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public final class VariableUtils {

    private VariableUtils() {
    }

    /**
     * 创建变量.
     *
     * @param name  变量名
     * @param value 变量值
     * @return 变量
     */
    public static Variable createVariable(String name, String value) {
        Variable variable = new Variable();
        variable.setName(name);
        variable.setValue(value);
        return variable;
    }

    /**
     * 获取变量值.
     *
     * @param variable 变量
     * @return 变量值
     */
    public static String value(Variable variable) {
        return variable == null ? null : variable.getValue();
    }
}

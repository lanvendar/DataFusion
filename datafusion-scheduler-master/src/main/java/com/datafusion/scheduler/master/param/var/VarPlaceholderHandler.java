package com.datafusion.scheduler.master.param.var;

import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.master.param.PlaceholderHandler;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量占位符处理.
 * 处理 #{变量名} 格式的变量替换.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class VarPlaceholderHandler implements PlaceholderHandler {

    @Override
    public String replacePlaceholders(String value, PlaceholderContext context) {
        if (value == null || context == null || context.getVariables() == null) {
            return value;
        }

        // 将 Map<String, Variable> 转换为 Map<String, String>
        Map<String, String> variableMap = new HashMap<>();
        for (Map.Entry<String, Variable> entry : context.getVariables().entrySet()) {
            Variable var = entry.getValue();
            if (var != null && var.getName() != null && var.getValue() != null) {
                variableMap.put(var.getName(), var.getValue());
            }
        }

        return VarPlaceholderUtils.replacePlaceholders(value, variableMap);
    }

    @Override
    public String getPrefix() {
        return VarPlaceholderUtils.PLACEHOLDER_PREFIX;
    }

    @Override
    public String getSuffix() {
        return VarPlaceholderUtils.PLACEHOLDER_SUFFIX;
    }
}

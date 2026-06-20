package com.datafusion.scheduler.master.param.exp;

import cn.hutool.core.util.StrUtil;
import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.master.param.PropertyPlaceholderHelper;
import com.datafusion.scheduler.master.param.exp.func.BuiltinFunc;
import com.datafusion.scheduler.master.param.exp.func.BuiltinFuncFactory;
import com.datafusion.scheduler.model.Variable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 表达式占位符处理工具类.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class ExpPlaceholderUtils {

    /**
     * 占位符前缀.
     */
    public static final String PLACEHOLDER_PREFIX = "#[";

    /**
     * 占位符后缀.
     */
    public static final String PLACEHOLDER_SUFFIX = "]";

    /**
     * 占位符替换器单例.
     */
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX, null, true);

    /**
     * 表达式占位符替换器实现类.
     */
    @AllArgsConstructor
    private static class ExpPlaceholderResolver implements PropertyPlaceholderHelper.PlaceholderResolver {

        /**
         * 占位符上下文.
         */
        private PlaceholderContext context;

        @Override
        public String resolvePlaceholder(String placeholderName) {
            try {
                placeholderName = placeholderName.trim();
                String funcName = null;

                // 解析函数名
                if (placeholderName.contains(FuncPlaceholderUtils.PLACEHOLDER_PREFIX)) {
                    funcName = placeholderName.substring(0,
                            placeholderName.indexOf(FuncPlaceholderUtils.PLACEHOLDER_PREFIX));
                    placeholderName = placeholderName.substring(
                            placeholderName.indexOf(FuncPlaceholderUtils.PLACEHOLDER_PREFIX) + 1);
                }

                // 去除右括号
                if (placeholderName.contains(FuncPlaceholderUtils.PLACEHOLDER_SUFFIX)) {
                    placeholderName = placeholderName.substring(0,
                            placeholderName.indexOf(FuncPlaceholderUtils.PLACEHOLDER_SUFFIX));
                }

                // 如果没有函数名，默认 DAY 函数
                if (funcName == null) {
                    // 表达式没有函数名，如 #[biz_date]
                    // 等价于 #[DAY(biz_date)]
                    funcName = "DAY";
                } else {
                    funcName = funcName.toUpperCase();
                }

                // 解析参数
                String[] rawArgs = StrUtil.splitToArray(placeholderName, ",");

                // 从 variables 中解析内置参数的值
                String[] resolvedArgs = resolveBuiltinArgs(rawArgs);

                BuiltinFunc builtinFunc = BuiltinFuncFactory.getBuiltinFunc(funcName);
                if (builtinFunc != null) {
                    return builtinFunc.call(
                            context.getScheduleTime(),
                            null,
                            resolvedArgs);
                } else {
                    return null;
                }
            } catch (Exception ex) {
                log.error("resolve placeholder '#[]' in [ {} ]", placeholderName, ex);
                return null;
            }
        }

        /**
         * 从 variables 中解析内置参数的值.
         *
         * @param rawArgs 原始参数数组
         * @return 解析后的参数数组
         */
        private String[] resolveBuiltinArgs(String[] rawArgs) {
            if (rawArgs == null || context.getVariables() == null) {
                return rawArgs;
            }

            Map<String, Variable> variables = context.getVariables();
            String[] resolvedArgs = new String[rawArgs.length];

            for (int i = 0; i < rawArgs.length; i++) {
                String arg = rawArgs[i].trim();
                // 如果参数是内置参数（存在于 variables 中），获取其值
                if (variables.containsKey(arg)) {
                    Variable var = variables.get(arg);
                    resolvedArgs[i] = var != null ? var.getValue() : arg;
                } else {
                    resolvedArgs[i] = arg;
                }
            }

            return resolvedArgs;
        }
    }

    /**
     * 获取占位符替换器单例.
     *
     * @return PropertyPlaceholderHelper实例
     */
    private static PropertyPlaceholderHelper getPropertyPlaceholderHelper() {

        return PLACEHOLDER_HELPER;
    }

    /**
     * 替换字符串中所有格式为 {@code #[表达式]} 的占位符.
     *
     * @param value   包含待替换占位符的字符串
     * @param context 占位符上下文
     * @return 替换占位符后的字符串
     */
    public static String replacePlaceholders(String value, PlaceholderContext context) {
        PropertyPlaceholderHelper helper = getPropertyPlaceholderHelper();

        // 表达式占位符替换器
        return helper.replacePlaceholders(value, new ExpPlaceholderResolver(context));
    }
}

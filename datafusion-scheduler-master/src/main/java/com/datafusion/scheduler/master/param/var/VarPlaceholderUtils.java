package com.datafusion.scheduler.master.param.var;

import com.datafusion.scheduler.master.param.PropertyPlaceholderHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 变量占位符处理工具类.
 * 处理 #{变量名} 格式的变量替换.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class VarPlaceholderUtils {

    /**
     * 占位符前缀.
     */
    public static final String PLACEHOLDER_PREFIX = "#{";
    /**
     * 占位符后缀.
     */
    public static final String PLACEHOLDER_SUFFIX = "}";

    /**
     * 占位符替换器单例.
     */
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX, null, true);

    /**
     * 变量占位符替换器实现类.
     */
    private static class VarPlaceholderResolver implements PropertyPlaceholderHelper.PlaceholderResolver {

        /**
         * 变量映射表.
         */
        private final Map<String, String> variableMap;

        VarPlaceholderResolver(Map<String, String> variableMap) {
            this.variableMap = variableMap;
        }

        @Override
        public String resolvePlaceholder(String placeholderName) {
            if (variableMap == null) {
                return null;
            }
            return variableMap.get(placeholderName.trim());
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
     * 替换字符串中所有格式为 {@code #{name}} 的占位符.
     *
     * @param value       包含待替换占位符的字符串
     * @param variableMap 包含变量名和变量值映射的Map
     * @return 替换占位符后的字符串
     */
    public static String replacePlaceholders(String value, Map<String, String> variableMap) {
        PropertyPlaceholderHelper helper = getPropertyPlaceholderHelper();
        return helper.replacePlaceholders(value, new VarPlaceholderResolver(variableMap));
    }
}

package com.datafusion.scheduler.master.param;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * 用于处理包含占位符的字符串的工具类.
 * 支持两种占位符格式:
 * <ul>
 *   <li>变量格式: {@code #{name}}</li>
 *   <li>表达式格式: {@code #[表达式]}</li>
 * </ul>
 * 使用 {@code PropertyPlaceholderHelper} 可以将占位符替换为用户提供的值.
 *
 * <p>
 * 替换值通过 {@link PlaceholderResolver} 提供.
 * </p>
 * 派生自 dolphin scheduler PropertyPlaceholderHelper.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author lanvendar
 * @version 3.0 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class PropertyPlaceholderHelper {

    /**
     * 已知简单的占位符对.
     */
    private static final Map<String, String> WELL_KNOWN_SIMPLE_PREFIXES = new HashMap<>(4);

    static {
        WELL_KNOWN_SIMPLE_PREFIXES.put("}", "{");
        WELL_KNOWN_SIMPLE_PREFIXES.put("]", "[");
        WELL_KNOWN_SIMPLE_PREFIXES.put(")", "(");
    }

    /**
     * 占位符前缀.
     */
    private final String placeholderPrefix;

    /**
     * 占位符后缀.
     */
    private final String placeholderSuffix;

    /**
     * 简单前缀.
     */
    private final String simplePrefix;

    /**
     * 值分隔符.
     */
    private final String valueSeparator;

    /**
     * 是否忽略无法解析的占位符.
     */
    private final boolean ignoreUnresolvablePlaceholders;

    /**
     * 创建一个使用指定前后缀的 {@code PropertyPlaceholderHelper} 实例.
     *
     * @param placeholderPrefix              占位符开始的前缀
     * @param placeholderSuffix              占位符结束的后缀
     * @param valueSeparator                 占位符变量与默认值之间的分隔字符(可选)
     * @param ignoreUnresolvablePlaceholders 是否忽略无法解析的占位符, true表示忽略, false表示抛出异常
     */
    public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix, String valueSeparator,
                                     boolean ignoreUnresolvablePlaceholders) {

        requireNonNull((Object) placeholderPrefix, "'placeholderPrefix' must not be null");
        requireNonNull((Object) placeholderSuffix, "'placeholderSuffix' must not be null");
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
        String simplePrefixForSuffix = WELL_KNOWN_SIMPLE_PREFIXES.get(this.placeholderSuffix);
        if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
            this.simplePrefix = simplePrefixForSuffix;
        } else {
            this.simplePrefix = this.placeholderPrefix;
        }
        this.valueSeparator = valueSeparator;
        this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
    }

    /**
     * 替换字符串中所有格式为 {@code ${name}} 的占位符.
     *
     * @param value               包含待替换占位符的字符串
     * @param placeholderResolver 用于解析占位符的 {@code PlaceholderResolver}
     * @return 替换占位符后的字符串
     */
    public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
        requireNonNull((Object) value, "'value' must not be null");
        return parseStringValue(value, placeholderResolver, new HashSet<>());
    }

    /**
     * 替换字符串中所有格式为 {@code ${name}} 的占位符.
     *
     * @param value               包含待替换占位符的字符串
     * @param placeholderResolver 用于解析占位符的 {@code PlaceholderResolver}
     * @param visitedPlaceholders 已访问的占位符集合(用于循环引用检测)
     * @return 替换占位符后的字符串
     */
    protected String parseStringValue(String value, PlaceholderResolver placeholderResolver,
                                      Set<String> visitedPlaceholders) {

        StringBuilder result = new StringBuilder(value);

        int startIndex = value.indexOf(this.placeholderPrefix);
        while (startIndex != -1) {
            int endIndex = findPlaceholderEndIndex(result, startIndex);
            if (endIndex != -1) {
                String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);
                String originalPlaceholder = placeholder;
                if (!visitedPlaceholders.add(originalPlaceholder)) {
                    throw new IllegalArgumentException(
                            "Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
                }
                // Recursive invocation, parsing placeholders contained in the placeholder key.
                placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);
                // Now obtain the value for the fully resolved key...
                String propVal = placeholderResolver.resolvePlaceholder(placeholder);
                if (propVal == null && this.valueSeparator != null) {
                    int separatorIndex = placeholder.indexOf(this.valueSeparator);
                    if (separatorIndex != -1) {
                        String actualPlaceholder = placeholder.substring(0, separatorIndex);
                        String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
                        propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
                        if (propVal == null) {
                            propVal = defaultValue;
                        }
                    }
                }
                if (propVal != null) {
                    // Recursive invocation, parsing placeholders contained in the
                    // previously resolved placeholder value.
                    propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
                    result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
                    if (log.isTraceEnabled()) {
                        log.trace("Resolved placeholder '" + placeholder + "'");
                    }
                    startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
                } else if (this.ignoreUnresolvablePlaceholders) {
                    // Proceed with unprocessed value.
                    startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
                } else {
                    throw new IllegalArgumentException(
                            "Could not resolve placeholder '" + placeholder + "'" + " in value \"" + value + "\"");
                }
                visitedPlaceholders.remove(originalPlaceholder);
            } else {
                startIndex = -1;
            }
        }

        return result.toString();
    }

    /**
     * 查找占位符结束的index.
     *
     * @param buf        string
     * @param startIndex 开始index
     * @return end index
     */
    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + this.placeholderPrefix.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (substringMatch(buf, index, this.placeholderSuffix)) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + this.placeholderSuffix.length();
                } else {
                    return index;
                }
            } else if (substringMatch(buf, index, this.simplePrefix)) {
                withinNestedPlaceholder++;
                index = index + this.simplePrefix.length();
            } else {
                index++;
            }
        }
        return -1;
    }

    /**
     * 用于解析字符串中占位符替换值的策略接口.
     */
    public interface PlaceholderResolver {

        /**
         * 解析占位符名称并返回替换值.
         *
         * @param placeholderName 要解析的占位符名称
         * @return 替换值,如果不需要替换则返回 {@code null}
         */
        String resolvePlaceholder(String placeholderName);
    }

    /**
     * 测试给定字符串在指定位置是否匹配给定子串.
     *
     * @param str       原始字符串(或StringBuilder)
     * @param index     原始字符串中开始匹配的位置
     * @param substring 要匹配的子串
     * @return 如果匹配则返回true,否则返回false
     */
    public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
        for (int j = 0; j < substring.length(); j++) {
            int i = index + j;
            if (i >= str.length() || str.charAt(i) != substring.charAt(j)) {
                return false;
            }
        }
        return true;
    }
}

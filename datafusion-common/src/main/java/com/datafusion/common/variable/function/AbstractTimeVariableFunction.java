package com.datafusion.common.variable.function;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.variable.VariableRenderContext;

import java.util.Date;
import java.util.List;

/**
 * 时间变量函数抽象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public abstract class AbstractTimeVariableFunction implements VariableFunction {

    /**
     * 默认日期格式.
     */
    public static final String DEFAULT_PATTERN = "yyyyMMddHHmmss";

    /**
     * 获取参数.
     *
     * @param arguments 参数列表
     * @param index     参数下标
     * @return 参数值
     */
    protected String argument(List<String> arguments, int index) {
        if (index >= arguments.size()) {
            return null;
        }
        String value = arguments.get(index);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\'", "'");
        }
        return trimmed;
    }

    /**
     * 解析基础日期.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础日期
     */
    protected Date resolveBaseDate(String base, VariableRenderContext context) {
        String value = resolveBaseValue(base, context);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (isTimestampLiteral(value)) {
            Long baseTime = parseLong(value);
            return baseTime == null ? null : new Date(baseTime);
        }
        return DateCalUtil.checkStringDate(value);
    }

    /**
     * 解析基础时间戳.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础时间戳
     */
    protected Long resolveBaseTime(String base, VariableRenderContext context) {
        String value = resolveBaseValue(base, context);
        return parseLong(value);
    }

    /**
     * 格式化时间.
     *
     * @param timestamp 时间戳
     * @return 格式化后的时间
     */
    public String formatTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DateCalUtil.format(new Date(timestamp), DEFAULT_PATTERN);
    }

    /**
     * 解析长整型.
     *
     * @param value 文本值
     * @return 长整型
     */
    public Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析基础参数值.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础参数值
     */
    private String resolveBaseValue(String base, VariableRenderContext context) {
        if (base == null || base.trim().isEmpty()) {
            Long defaultTimeMillis = context == null ? null : context.getDefaultTimeMillis();
            return defaultTimeMillis == null ? null : String.valueOf(defaultTimeMillis);
        }
        String trimmed = base.trim();
        if (context != null && context.getVariables() != null) {
            String value = context.getVariableValue(trimmed);
            if (value != null) {
                return value;
            }
        }
        return trimmed;
    }

    /**
     * 判断是否是毫秒时间戳字面量.
     *
     * @param value 文本值
     * @return 是否是毫秒时间戳字面量
     */
    private boolean isTimestampLiteral(String value) {
        return value != null && value.trim().matches("^\\d{13}$");
    }
}

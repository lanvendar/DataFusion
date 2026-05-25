package com.datafusion.scheduler.master.param.builtin;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.date.DateTimeStamp;
import com.datafusion.common.enums.TimeAlignmentEnum;
import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 内置参数解析器.
 *
 * <p>
 * 负责解析内置参数并填充到 variables 中:
 * <ul>
 *   <li>now_time   → 系统当前时间(毫秒)</li>
 *   <li>now_date   → 格式化后的当前时间</li>
 *   <li>biz_time   → 业务时间，按对齐格式计算</li>
 *   <li>biz_date   → 业务日期，按对齐格式计算</li>
 *   <li>biz_date_align → 对齐格式</li>
 * </ul>
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
@Slf4j
public class BuiltinParamResolver {

    /**
     * 默认日期格式.
     */
    private static final String DEFAULT_PATTERN = "yyyyMMddHHmmss";

    /**
     * 解析内置参数并填充到 context.variables 中.
     *
     * @param context 占位符上下文
     */
    public void resolveBuiltinParams(PlaceholderContext context) {
        if (context == null || context.getVariables() == null) {
            return;
        }

        // 解析内置参数
        // now_time: 系统当前时间(毫秒)
        Long nowTime = System.currentTimeMillis();
        context.getVariables().put(BuiltinParamEnum.NOW_TIME.getParamName(),
                createVariable(BuiltinParamEnum.NOW_TIME.getParamName(), String.valueOf(nowTime)));

        // now_date: 格式化后的当前时间
        context.getVariables().put(BuiltinParamEnum.NOW_DATE.getParamName(),
                createVariable(BuiltinParamEnum.NOW_DATE.getParamName(), formatTime(nowTime, DEFAULT_PATTERN)));

        String bizDateAlign = context.getBizDateAlign();
        // biz_date_align: 对齐格式
        if (bizDateAlign != null) {
            context.getVariables().put(BuiltinParamEnum.BIZ_DATE_ALIGN.getParamName(),
                    createVariable(BuiltinParamEnum.BIZ_DATE_ALIGN.getParamName(), bizDateAlign));
        }

        Long scheduleTime = context.getScheduleTime();
        // biz_time 和 biz_date
        if (scheduleTime != null) {
            Long bizTime = scheduleTime;
            String bizDate = formatTime(scheduleTime, DEFAULT_PATTERN);

            // 如果有对齐格式，按对齐格式计算
            if (bizDateAlign != null && !bizDateAlign.isEmpty()) {
                TimeAlignmentEnum alignment = TimeAlignmentEnum.getByCode(bizDateAlign);
                if (alignment != null) {
                    Long alignedTime = DateTimeStamp.getTime(scheduleTime, bizDateAlign);
                    bizTime = alignedTime;
                    bizDate = formatTime(alignedTime, DEFAULT_PATTERN);
                }
            }

            context.getVariables().put(BuiltinParamEnum.BIZ_TIME.getParamName(),
                    createVariable(BuiltinParamEnum.BIZ_TIME.getParamName(), String.valueOf(bizTime)));
            context.getVariables().put(BuiltinParamEnum.BIZ_DATE.getParamName(),
                    createVariable(BuiltinParamEnum.BIZ_DATE.getParamName(), bizDate));
        }

        log.debug("Built-in parameters resolved: {}", context.getVariables().keySet());
    }

    /**
     * 解析单个内置参数的值.
     *
     * @param paramName 参数名
     * @param context   上下文
     * @return 参数值
     */
    public String resolveParam(String paramName, PlaceholderContext context) {
        if (paramName == null || context == null || context.getVariables() == null) {
            return null;
        }

        Variable variable = context.getVariables().get(paramName);
        if (variable != null && variable.getValue() != null) {
            return variable.getValue();
        }

        return null;
    }

    /**
     * 创建 Variable 对象.
     *
     * @param name  名称
     * @param value 值
     * @return Variable 对象
     */
    private Variable createVariable(String name, String value) {
        Variable var = new Variable();
        var.setName(name);
        var.setValue(value);
        return var;
    }

    /**
     * 格式化时间.
     *
     * @param timestamp 时间戳
     * @param pattern   格式
     * @return 格式化后的字符串
     */
    private String formatTime(Long timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        try {
            return DateCalUtil.format(new Date(timestamp), pattern);
        } catch (Exception e) {
            log.warn("Format time failed: timestamp={}, pattern={}", timestamp, pattern, e);
            return String.valueOf(timestamp);
        }
    }
}

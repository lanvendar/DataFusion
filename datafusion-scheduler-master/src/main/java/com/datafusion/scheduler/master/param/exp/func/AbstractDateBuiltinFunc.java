package com.datafusion.scheduler.master.param.exp.func;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.date.DateTimeStamp;
import com.datafusion.scheduler.master.param.builtin.BuiltinParamEnum;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 日期内置函数抽象类.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/22
 * @since 2022/6/22
 */
@Slf4j
public abstract class AbstractDateBuiltinFunc implements BuiltinFunc {

    @Override
    public String call(Long scheduleTime, String align, String... args) {
        Preconditions.checkArgument(args.length >= 1, "参数数量必须大于等于1");
        log.debug("时间函数[{}]处理,scheduleTime={},align={},args={}", this.name(), scheduleTime, align, args);

        final BuiltinParamEnum builtinParamEnum = BuiltinParamEnum.getByParamName(args[0]);

        String pattern = "yyyyMMddHHmmss";
        String offsetExp = null;
        String suffixExp = null;

        if (args.length >= 2) {
            offsetExp = args[1];
        }
        if (args.length >= 3) {
            pattern = args[2];
        }
        if (args.length >= 4) {
            suffixExp = args[3];
        }

        Date baseDate = null;
        if (builtinParamEnum != null) {
            switch (builtinParamEnum) {
                case NOW_TIME:
                case NOW_DATE:
                    // now_time / now_date -> 系统当前时间
                    baseDate = new Date(System.currentTimeMillis());
                    break;
                case SCHEDULE_TIME:
                    if (scheduleTime != null) {
                        baseDate = new Date(scheduleTime);
                    }
                    break;
                case BIZ_TIME:
                case BIZ_DATE:
                case EVENT_TIME:
                case EVENT_DATE:
                    // biz_time / biz_date
                    if (align != null && !align.isEmpty()) {
                        // 有对齐格式 -> 按对齐格式返回
                        Long alignedTime = DateTimeStamp.getTime(scheduleTime, align);
                        baseDate = new Date(alignedTime);
                    } else {
                        // 无对齐格式 -> 使用 scheduleTime
                        if (scheduleTime != null) {
                            baseDate = new Date(scheduleTime);
                        }
                    }
                    break;
                default:
                    if (scheduleTime != null) {
                        baseDate = new Date(scheduleTime);
                    }
                    break;
            }
        } else {
            log.debug("时间参数为非内置参数，[{}]", args[0]);

            try {
                long timestamp = Long.parseLong(args[0]);
                log.warn("时间参数为时间戳");
                baseDate = new Date(timestamp);
            } catch (Exception e) {
                log.warn("时间参数为日期格式");
                baseDate = DateCalUtil.checkStringDate(args[0]);
            }
        }

        if (baseDate == null) {
            return null;
        }

        baseDate = DateCalUtil.calDateExp(baseDate, offsetExp, suffixExp);

        return getResult(baseDate, pattern);
    }

    /**
     * 获取最终结果抽象方法.
     *
     * @param date    日期
     * @param pattern 格式
     * @return 结果
     */
    protected abstract String getResult(Date date, String pattern);
}

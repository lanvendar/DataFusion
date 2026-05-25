package com.datafusion.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 时间对齐枚举类.
 *
 * <p>
 * 用于定义时间维度的对齐方式，支持以下类型：
 * <ul>
 *   <li>基础周期：按指定时间单位取整（如按分钟、小时、天、月等）</li>
 *   <li>下一周期：取当前时间单位的下一个周期边界</li>
 *   <li>时区偏移：基于GMT+8时区的偏移处理（用于Cassandra时区转换）</li>
 * </ul>
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/14
 * @since 2021/09/18
 */
@Getter
public enum TimeAlignmentEnum {

    //--------------------------原始时间------------------------

    /**
     * 原始时间（不做任何取整）.
     */
    ORIGINAL("original"),

    //--------------------------周期------------------------

    /**
     * 按5分钟取整.
     */
    MINUTE_5("minute_5"),

    /**
     * 按10分钟取整.
     */
    MINUTE_10("minute_10"),

    /**
     * 按15分钟取整.
     */
    MINUTE_15("minute_15"),

    /**
     * 按30分钟取整.
     */
    MINUTE_30("minute_30"),

    /**
     * 按1小时取整.
     */
    HOUR_1("hour_1"),

    /**
     * 按1天取整（取当天零点）.
     */
    DAY_1("day_1"),

    /**
     * 按1月取整（取当月第一天）.
     */
    MONTH_1("month_1"),

    /**
     * 按1季度取整（取当季第一天）.
     */
    MONTH_3("month_3"),

    /**
     * 按1年取整（取当年第一天）.
     */
    YEAR_1("year_1"),

    //-----------------------月末,年末------------------------

    /**
     * 年末（取当年最后一天）.
     */
    YEAR_END("year_end"),

    /**
     * 月末（取当月最后一天）.
     */
    MONTH_END("month_end"),

    //-----------------------下一周期------------------------

    /**
     * 下一周期-5分钟（向下取整到下一个5分钟边界）.
     */
    MINUTE_5_NEXT("minute_5_next"),

    /**
     * 下一周期-10分钟（向下取整到下一个10分钟边界）.
     */
    MINUTE_10_NEXT("minute_10_next"),

    /**
     * 下一周期-15分钟（向下取整到下一个15分钟边界）.
     */
    MINUTE_15_NEXT("minute_15_next"),

    /**
     * 下一周期-30分钟（向下取整到下一个30分钟边界）.
     */
    MINUTE_30_NEXT("minute_30_next"),

    /**
     * 下一周期-1小时（取下一小时整点）.
     */
    HOUR_1_NEXT("hour_1_next"),

    /**
     * 下一周期-1天（取第二天零点）.
     */
    DAY_1_NEXT("day_1_next"),

    /**
     * 下一周期-1月（取下月第一天）.
     */
    MONTH_1_NEXT("month_1_next"),

    /**
     * 下一周期-1季度（取下季度第一天）.
     */
    MONTH_3_NEXT("month_3_next"),

    /**
     * 下一周期-1年（取下一年第一天）.
     */
    YEAR_1_NEXT("year_1_next"),

    //-----------------------修正8小时---------------------------

    /**
     * 原始时间 +8小时时区偏移.
     */
    ORIGINAL_ADD_8("original_add_8"),

    /**
     * 按5分钟取整 +8小时时区偏移.
     */
    MINUTE_5_ADD_8("minute_5_add_8"),

    /**
     * 按10分钟取整 +8小时时区偏移.
     */
    MINUTE_10_ADD_8("minute_10_add_8"),

    /**
     * 按15分钟取整 +8小时时区偏移.
     */
    MINUTE_15_ADD_8("minute_15_add_8"),

    /**
     * 按30分钟取整 +8小时时区偏移.
     */
    MINUTE_30_ADD_8("minute_30_add_8"),

    /**
     * 按1小时取整 +8小时时区偏移.
     */
    HOUR_1_ADD_8("hour_1_add_8"),

    /**
     * 按1天取整 +8小时时区偏移.
     */
    DAY_1_ADD_8("day_1_add_8"),

    /**
     * 按1月取整 +8小时时区偏移.
     */
    MONTH_1_ADD_8("month_1_add_8"),

    /**
     * 按1季度取整 +8小时时区偏移.
     */
    MONTH_3_ADD_8("month_3_add_8"),

    /**
     * 按1年取整 +8小时时区偏移.
     */
    YEAR_1_ADD_8("year_1_add_8"),

    /**
     * 下一周期-5分钟 +8小时时区偏移.
     */
    MINUTE_5_NEXT_ADD_8("minute_5_next_add_8"),

    /**
     * 下一周期-10分钟 +8小时时区偏移.
     */
    MINUTE_10_NEXT_ADD_8("minute_10_next_add_8"),

    /**
     * 下一周期-15分钟 +8小时时区偏移.
     */
    MINUTE_15_NEXT_ADD_8("minute_15_next_add_8"),

    /**
     * 下一周期-30分钟 +8小时时区偏移.
     */
    MINUTE_30_NEXT_ADD_8("minute_30_next_add_8"),

    /**
     * 下一周期-1小时 +8小时时区偏移.
     */
    HOUR_1_NEXT_ADD_8("hour_1_next_add_8"),

    /**
     * 下一周期-1天 +8小时时区偏移.
     */
    DAY_1_NEXT_ADD_8("day_1_next_add_8"),

    /**
     * 下一周期-1月 +8小时时区偏移.
     */
    MONTH_1_NEXT_ADD_8("month_1_next_add_8"),

    /**
     * 下一周期-1季度 +8小时时区偏移.
     */
    MONTH_3_NEXT_ADD_8("month_3_next_add_8"),

    /**
     * 下一周期-1年 +8小时时区偏移.
     */
    YEAR_1_NEXT_ADD_8("year_1_next_add_8"),

    /**
     * 月末 +8小时时区偏移.
     */
    MONTH_END_ADD_8("month_end_add_8"),

    /**
     * 年末 +8小时时区偏移.
     */
    YEAR_END_ADD_8("year_end_add_8");

    /**
     * 编码.
     */
    private final String code;

    TimeAlignmentEnum(String code) {
        this.code = code;
    }

    /**
     * 根据code获取枚举.
     *
     * @param code 编码
     * @return 枚举值
     */
    public static TimeAlignmentEnum getByCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}

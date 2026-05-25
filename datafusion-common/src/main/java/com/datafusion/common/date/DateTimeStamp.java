package com.datafusion.common.date;

import com.datafusion.common.enums.TimeAlignmentEnum;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 时间工具类. 因Cassandra内置时间为UTC时间, 会根据服务器所属时区不同自动转换时区, 故由此类计算
 *
 * @author lanvendar
 * @version 3.0.0, 2021/09/18
 * @since 2021/09/18
 */
public class DateTimeStamp {

    /**
     * 5分钟维度.
     */
    private static final int MINUTE_5 = 5;

    /**
     * 10分钟维度.
     */
    private static final int MINUTE_10 = 10;

    /**
     * 15分钟维度.
     */
    private static final int MINUTE_15 = 15;

    /**
     * 30分钟维度.
     */
    private static final int MINUTE_30 = 30;

    /**
     * 时间格式.
     */
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    /**
     * 时区.
     */
    private static final String ZONE_8 = "GMT+8";

    /**
     * 下一周期映射表.
     */
    private static final Map<String, String> NEXT_PERIOD_MAP = new HashMap<>();

    /**
     * +8小时偏移映射表.
     */
    private static final Map<String, String> ADD_8_MAP = new HashMap<>();

    static {
        // 初始化下一周期映射
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.ORIGINAL.getCode(), TimeAlignmentEnum.ORIGINAL.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MINUTE_5.getCode(), TimeAlignmentEnum.MINUTE_5_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MINUTE_10.getCode(), TimeAlignmentEnum.MINUTE_10_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MINUTE_15.getCode(), TimeAlignmentEnum.MINUTE_15_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MINUTE_30.getCode(), TimeAlignmentEnum.MINUTE_30_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.HOUR_1.getCode(), TimeAlignmentEnum.HOUR_1_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.DAY_1.getCode(), TimeAlignmentEnum.DAY_1_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MONTH_1.getCode(), TimeAlignmentEnum.MONTH_1_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.MONTH_3.getCode(), TimeAlignmentEnum.MONTH_3_NEXT.getCode());
        NEXT_PERIOD_MAP.put(TimeAlignmentEnum.YEAR_1.getCode(), TimeAlignmentEnum.YEAR_1_NEXT.getCode());

        // 初始化+8小时偏移映射
        ADD_8_MAP.put(TimeAlignmentEnum.ORIGINAL.getCode(), TimeAlignmentEnum.ORIGINAL_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_5.getCode(), TimeAlignmentEnum.MINUTE_5_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_10.getCode(), TimeAlignmentEnum.MINUTE_10_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_15.getCode(), TimeAlignmentEnum.MINUTE_15_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_30.getCode(), TimeAlignmentEnum.MINUTE_30_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.HOUR_1.getCode(), TimeAlignmentEnum.HOUR_1_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.DAY_1.getCode(), TimeAlignmentEnum.DAY_1_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MONTH_1.getCode(), TimeAlignmentEnum.MONTH_1_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MONTH_3.getCode(), TimeAlignmentEnum.MONTH_3_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.YEAR_1.getCode(), TimeAlignmentEnum.YEAR_1_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_5_NEXT.getCode(), TimeAlignmentEnum.MINUTE_5_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_10_NEXT.getCode(), TimeAlignmentEnum.MINUTE_10_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_15_NEXT.getCode(), TimeAlignmentEnum.MINUTE_15_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MINUTE_30_NEXT.getCode(), TimeAlignmentEnum.MINUTE_30_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.HOUR_1_NEXT.getCode(), TimeAlignmentEnum.HOUR_1_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.DAY_1_NEXT.getCode(), TimeAlignmentEnum.DAY_1_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MONTH_1_NEXT.getCode(), TimeAlignmentEnum.MONTH_1_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.MONTH_3_NEXT.getCode(), TimeAlignmentEnum.MONTH_3_NEXT_ADD_8.getCode());
        ADD_8_MAP.put(TimeAlignmentEnum.YEAR_1_NEXT.getCode(), TimeAlignmentEnum.YEAR_1_NEXT_ADD_8.getCode());
    }

    /**
     * 解析时间对齐枚举.
     *
     * @param timeNature 时间维度字符串
     * @return 枚举值，不存在返回null
     */
    private static TimeAlignmentEnum parseAlignmentEnum(String timeNature) {
        TimeAlignmentEnum alignmentEnum = TimeAlignmentEnum.getByCode(timeNature);
        if (alignmentEnum == null) {
            alignmentEnum = TimeAlignmentEnum.getByCode(timeNature.toLowerCase());
        }
        return alignmentEnum;
    }

    /**
     * 按当前时间取参数对应时间.
     *
     * @param timestamp  时间戳
     * @param timeNature 时间维度枚举
     * @return 时间戳
     * @see TimeAlignmentEnum
     */
    public static Long getTimeFromNature(Long timestamp, String timeNature) {
        TimeAlignmentEnum alignmentEnum = parseAlignmentEnum(timeNature);
        if (alignmentEnum != null) {
            String code = alignmentEnum.getCode();
            // 判断是否需要+8小时偏移
            if (code.endsWith("_add_8")) {
                String baseCode = code.replace("_add_8", "");
                return insertTimeAdd8(getTime(timestamp, baseCode));
            }
            return getTime(timestamp, code);
        }
        return getTime(timestamp, timeNature);
    }

    /**
     * +8小时修正. GTM+8 --> GTM+0
     *
     * @param timestamp 时间戳
     * @return 时间戳
     */
    public static Long insertTimeAdd8(Long timestamp) {
        return insertTimeAddZone(timestamp, ZONE_8);
    }

    /**
     * +8小时修正. GTM+8 --> GTM+0
     *
     * @param timestamp 时间戳
     * @param zone      默认+8时区
     * @return 时间戳
     */
    public static Long insertTimeAddZone(Long timestamp, String zone) {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone(zone));
        c.setTime(new Date(timestamp));
        //取得时间偏移量
        int zoneOffset = c.get(Calendar.ZONE_OFFSET);
        //取得夏令时差
        int dstOffset = c.get(Calendar.DST_OFFSET);
        //从本地时间里加上这些差量，即可以取得UTC时间
        c.add(Calendar.MILLISECOND, (zoneOffset + dstOffset));

        new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS).format(new Date(timestamp));
        return c.getTimeInMillis();
    }

    /**
     * -8小时修正. GTM+0 --> GTM+8
     *
     * @param timestamp 时间戳
     * @return 时间戳
     */
    public static Long selectTimeReduce8(Long timestamp) {
        return reduceTimeAddZone(timestamp, ZONE_8);
    }

    /**
     * -8小时修正. GTM+0 --> GTM+8
     *
     * @param timestamp 时间戳
     * @param zone      默认+8时区
     * @return 时间戳
     */
    public static Long reduceTimeAddZone(Long timestamp, String zone) {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone(zone));
        c.setTime(new Date(timestamp));
        //取得时间偏移量
        int zoneOffset = c.get(Calendar.ZONE_OFFSET);
        //取得夏令时差
        int dstOffset = c.get(Calendar.DST_OFFSET);
        //从本地时间里加上这些差量，即可以取得UTC时间
        c.add(Calendar.MILLISECOND, -(zoneOffset + dstOffset));

        new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS).format(new Date(timestamp));
        return c.getTimeInMillis();
    }

    /**
     * 时间戳 timestamp 转 String 类型.
     *
     * @param timestamp 时间戳
     * @return String 类型 yyyyMMddHHmmss
     */
    public static String getTimeFormat(Long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * 时间戳 timestamp 转 Date类型.
     *
     * @param timestamp 时间戳
     * @return Date类型
     */
    public static Date getTimeDate(Long timestamp) {
        return new Date(timestamp);
    }

    /**
     * 取下一个周期的枚举.
     *
     * @param dimensions 周期
     * @return 下一个周期的枚举
     */
    public static String getNextTimeNature(String dimensions) {
        TimeAlignmentEnum alignmentEnum = parseAlignmentEnum(dimensions);
        if (alignmentEnum != null) {
            String nextCode = NEXT_PERIOD_MAP.get(alignmentEnum.getCode());
            if (nextCode != null) {
                return nextCode;
            }
        }
        throw new RuntimeException("无效内置时间枚举");
    }

    /**
     * 取下一周期+8小时的枚举.
     *
     * @param dimensions 周期
     * @return 下一周期+8小时的枚举
     */
    public static String getAdd8TimeNature(String dimensions) {
        TimeAlignmentEnum alignmentEnum = parseAlignmentEnum(dimensions);
        if (alignmentEnum != null) {
            String baseCode = alignmentEnum.getCode().replace("_next", "");
            String add8Code = ADD_8_MAP.get(baseCode);
            if (add8Code != null) {
                return add8Code;
            }
        }
        throw new RuntimeException("无效内置时间枚举");
    }

    /**
     * 时间戳 timestamp 按年月日取整.
     *
     * @param timestamp  时间戳
     * @param dimensions 时间维度枚举
     * @return 时间戳Long类型
     */
    public static Long getTime(Long timestamp, String dimensions) {
        return getTimeWithZone(timestamp, dimensions.toLowerCase(), ZONE_8);
    }

    /**
     * 时间戳 timestamp 按年月日取整.
     *
     * @param timestamp  时间戳
     * @param dimensions 时间维度枚举
     * @param zone       默认+8 时区
     * @return 修正时区后的时间
     */
    private static Long getTimeWithZone(Long timestamp, String dimensions, String zone) {
        Calendar c = createCalendar(timestamp, zone);
        TimeAlignmentEnum alignmentEnum = parseAlignmentEnum(dimensions);
        if (alignmentEnum != null) {
            switch (alignmentEnum) {
                case ORIGINAL:
                    return c.getTimeInMillis();
                case MINUTE_5:
                case MINUTE_10:
                case MINUTE_15:
                case MINUTE_30:
                case MINUTE_5_NEXT:
                case MINUTE_10_NEXT:
                case MINUTE_15_NEXT:
                case MINUTE_30_NEXT:
                    return calMinute(c, dimensions);
                case HOUR_1:
                case HOUR_1_NEXT:
                    return calHour(c, dimensions);
                case DAY_1:
                case DAY_1_NEXT:
                    return calDay(c, dimensions);
                case MONTH_1:
                case MONTH_1_NEXT:
                case MONTH_3:
                case MONTH_3_NEXT:
                case MONTH_END:
                    return calMonth(c, dimensions);
                case YEAR_1:
                case YEAR_1_NEXT:
                case YEAR_END:
                    return calYear(c, dimensions);
                default:
                    throw new RuntimeException("无效内置时间枚举");
            }
        }
        throw new RuntimeException("无效内置时间枚举");
    }

    /**
     * 创建Calendar并设置时区.
     *
     * @param timestamp 时间戳
     * @param zone     时区
     * @return Calendar对象
     */
    private static Calendar createCalendar(Long timestamp, String zone) {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone(zone));
        c.setTime(new Date(timestamp));
        return c;
    }

    /**
     * 年相关计算.
     *
     * @param c          日历对象
     * @param dimensions 时间维度枚举
     * @return 时间戳
     */
    private static Long calYear(Calendar c, String dimensions) {
        resetToDayStart(c);
        if (TimeAlignmentEnum.YEAR_1.getCode().equals(dimensions)) {
            c.set(Calendar.DAY_OF_YEAR, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.YEAR_1_NEXT.getCode().equals(dimensions)) {
            c.add(Calendar.YEAR, 1);
            c.set(Calendar.DAY_OF_YEAR, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.YEAR_END.getCode().equals(dimensions)) {
            c.add(Calendar.YEAR, 1);
            c.set(Calendar.DAY_OF_YEAR, 0);
            return c.getTimeInMillis();
        }
        return c.getTimeInMillis();
    }

    /**
     * 月相关计算.
     *
     * @param c          日历对象
     * @param dimensions 时间维度枚举
     * @return 时间戳
     */
    private static Long calMonth(Calendar c, String dimensions) {
        resetToDayStart(c);
        if (TimeAlignmentEnum.MONTH_1.getCode().equals(dimensions)) {
            c.set(Calendar.DAY_OF_MONTH, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.MONTH_1_NEXT.getCode().equals(dimensions)) {
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.MONTH_3.getCode().equals(dimensions)) {
            int month = getQuarterStartMonth(c.get(Calendar.MONTH));
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.MONTH_3_NEXT.getCode().equals(dimensions)) {
            int month = getQuarterStartMonth(c.get(Calendar.MONTH)) + 3;
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, 1);
            return c.getTimeInMillis();
        } else if (TimeAlignmentEnum.MONTH_END.getCode().equals(dimensions)) {
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 0);
            return c.getTimeInMillis();
        }
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /**
     * 获取季度起始月份.
     *
     * @param month 当前月份
     * @return 季度起始月份（0-based）
     */
    private static int getQuarterStartMonth(int month) {
        int[] quarterStartMonths = {0, 3, 6, 9};
        return quarterStartMonths[month / 3];
    }

    /**
     * 天相关计算.
     *
     * @param c          日历对象
     * @param dimensions 时间维度枚举
     * @return 时间戳
     */
    private static Long calDay(Calendar c, String dimensions) {
        resetToDayStart(c);
        if (TimeAlignmentEnum.DAY_1_NEXT.getCode().equals(dimensions)) {
            c.add(Calendar.DATE, 1);
        }
        return c.getTimeInMillis();
    }

    /**
     * 小时相关计算.
     *
     * @param c          日历对象
     * @param dimensions 时间维度枚举
     * @return 时间戳
     */
    private static Long calHour(Calendar c, String dimensions) {
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.MINUTE, 0);
        if (TimeAlignmentEnum.HOUR_1_NEXT.getCode().equals(dimensions)) {
            c.add(Calendar.HOUR_OF_DAY, 1);
        }
        return c.getTimeInMillis();
    }

    /**
     * 分钟相关计算.
     *
     * @param c          日历对象
     * @param dimensions 时间维度枚举
     * @return 时间戳
     */
    private static Long calMinute(Calendar c, String dimensions) {
        int offset = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) > 0 ? 1 : 0;
        int minute = c.get(Calendar.MINUTE);

        if (dimensions.contains("_next")) {
            // 下一周期：向上取整
            if (TimeAlignmentEnum.MINUTE_5.getCode().equals(dimensions)) {
                minute = (int) Math.ceil((minute + offset) / MINUTE_5) * MINUTE_5;
            } else if (TimeAlignmentEnum.MINUTE_10.getCode().equals(dimensions)) {
                minute = (int) Math.ceil((minute + offset) / MINUTE_10) * MINUTE_10;
            } else if (TimeAlignmentEnum.MINUTE_15.getCode().equals(dimensions)) {
                minute = (int) Math.ceil((minute + offset) / MINUTE_15) * MINUTE_15;
            } else if (TimeAlignmentEnum.MINUTE_30.getCode().equals(dimensions)) {
                minute = (int) Math.ceil((minute + offset) / MINUTE_30) * MINUTE_30;
            }
        } else {
            // 当前周期：向下取整
            if (TimeAlignmentEnum.MINUTE_5.getCode().equals(dimensions)) {
                minute = (int) (Math.floor(minute / MINUTE_5) * MINUTE_5);
            } else if (TimeAlignmentEnum.MINUTE_10.getCode().equals(dimensions)) {
                minute = (int) (Math.floor(minute / MINUTE_10) * MINUTE_10);
            } else if (TimeAlignmentEnum.MINUTE_15.getCode().equals(dimensions)) {
                minute = (int) (Math.floor(minute / MINUTE_15) * MINUTE_15);
            } else if (TimeAlignmentEnum.MINUTE_30.getCode().equals(dimensions)) {
                minute = (int) (Math.floor(minute / MINUTE_30) * MINUTE_30);
            }
        }
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * 重置到当天开始（零点）.
     *
     * @param c 日历对象
     */
    private static void resetToDayStart(Calendar c) {
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.HOUR_OF_DAY, 0);
    }

    /**
     * 判断是否是同一时间维度.
     *
     * @param dimensions 周期特征
     * @param firstTs    时间参数1
     * @param secondTs   时间参数2
     * @return 是否同一时间维度
     */
    public static Boolean isSameTimeNature(Long firstTs, Long secondTs, String dimensions) {
        Long firstTime = getTimeFromNature(firstTs, dimensions);
        Long secondTime = getTimeFromNature(secondTs, dimensions);
        return firstTime.longValue() == secondTime.longValue();
    }
}

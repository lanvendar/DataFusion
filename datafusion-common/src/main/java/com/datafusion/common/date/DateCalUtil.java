package com.datafusion.common.date;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * sql模板日期计算类.
 *
 * @author liuyanwei
 * @version 3.0.0, 2020/11/30
 * @since 2020/11/30
 */
@Slf4j
public class DateCalUtil {

    /**
     * 预设时间格式 yyyyMMdd .
     */
    public static final String FORMAT_STD = "yyyyMMddHHmmss";

    /**
     * 普通日期格式中特殊符号- : 空格 /.
     */
    private static final String FORMAT_SPECIAL_CHAR_REGEX = "[-|/|:\\s]";

    /**
     * 正则判断是否是有效数字.
     */
    private static final Pattern IS_NUMBER = Pattern.compile("^-?[1-9]+[0-9]*$|^0$");

    /**
     * 代表分钟.
     */
    private static final String TIME_TYPE_MN = "m";

    /**
     * 代表小时.
     */
    private static final String TIME_TYPE_H = "H";

    /**
     * 代表天/日.
     */
    private static final String TIME_TYPE_D = "D";

    /**
     * 代表月.
     */
    private static final String TIME_TYPE_M = "M";

    /**
     * 代表年.
     */
    private static final String TIME_TYPE_Y = "Y";

    /**
     * 代表秒.
     */
    private static final String TIME_TYPE_S = "S";

    /**
     * 所在日期的周的第一天，默认周一.
     */
    public static final String DAY_FUNC_PARAM_WS = "WS";

    /**
     * 所在日期的周的最后一天，默认周日.
     */
    public static final String DAY_FUNC_PARAM_WD = "WD";

    /**
     * 所在日期的月末.
     */
    public static final String DAY_FUNC_PARAM_MD = "MD";

    /**
     * 所在日期的月初.
     */
    public static final String DAY_FUNC_PARAM_MS = "MS";

    /**
     * 所在日期的季末.
     */
    public static final String DAY_FUNC_PARAM_SD = "SD";

    /**
     * 所在日期的季初.
     */
    public static final String DAY_FUNC_PARAM_SS = "SS";

    /**
     * 所在日期的年末.
     */
    public static final String DAY_FUNC_PARAM_YD = "YD";

    /**
     * 所在日期的年初.
     */
    public static final String DAY_FUNC_PARAM_YS = "YS";

    /**
     * 格式化日期.
     *
     * @param date    : 基础日期
     * @param pattern : 日期格式
     * @return 格式化后的日期字符串
     */
    public static String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    /**
     * 计算规则(day,-1D,yyyyMMdd,MD): 先计算偏移量-1D,再计算结尾参数枚举,最后格式化yyyyMMdd.
     *
     * @param baseDate  : 基础日期
     * @param offset    : -1D
     * @param suffixExp : 结尾参数
     * @param pattern   : 日期格式
     * @return 计算后的日期字符串，如果无法解析或者无法计算，则直接返回dateStr
     */
    public static String calDateExpFormat(Date baseDate, String offset, String suffixExp, String pattern) {
        Date date = calDateExp(baseDate, offset, suffixExp);
        if (null == pattern || pattern.isEmpty()) {
            pattern = FORMAT_STD;
        }
        return format(date, pattern);
    }

    /**
     * 计算规则(day,-1D,yyyyMMdd,MD): 先计算偏移量-1D,再计算结尾参数枚举,最后格式化yyyyMMdd.
     *
     * @param baseDate  : 基础日期
     * @param offset    : -1D
     * @param suffixExp : 结尾参数
     * @return 计算后的日期字符串，如果无法解析或者无法计算，则直接返回dateStr
     */
    public static Date calDateExp(Date baseDate, String offset, String suffixExp) {
        try {
            if (offset != null) {
                baseDate = calDateByOffset(baseDate, offset);
            }

            if (suffixExp != null) {
                baseDate = calDateBySuffixExp(baseDate, suffixExp);
            }

            return baseDate;
        } catch (Exception e) {
            log.warn("时间转换或者计算失败,{}", e.getMessage());
            throw new RuntimeException("sql中日期计算格式错误[day,-1D,YYYYMMDD]");
        }
    }

    /**
     * 根据指定的date字符串转换成Date，支持yyyy MM dd HH mm ss格式，且支持四种分隔符- / 空格 : 注意：必须以年月日时分秒排序，末尾可以省略 如 年月日时，但是头部不能省略 如 月日时.
     *
     * @param date 日期,默认格式 yyyyMMdd
     * @return Date     date类型对象
     */
    public static Date checkStringDate(String date) {
        try {
            String dateStr = date.replaceAll(FORMAT_SPECIAL_CHAR_REGEX, "");

            int length = dateStr.length();

            String format = FORMAT_STD.substring(0, length);

            return DateUtil.parse(dateStr, format);
        } catch (Exception e) {
            log.warn("时间格式解析失败,{}", e.getMessage());
            throw new RuntimeException("sql中日期计算格式错误[day,-1D,YYYYMMDD]");
        }
    }

    /**
     * 根据日期偏移量设置日期,依据正则: ^-?[0-9]*([DMY]?)$ -1 或 -1D 前一日,30 或 30D 后30日, -1M 前一月,1M 后一月, -1Y 前一年,1Y 后一年.
     *
     * @param date      日期,默认格式 yyyyMMdd
     * @param offsetExp 日期偏移量表达式
     * @return Date
     */
    private static Date calDateByOffset(Date date, String offsetExp) {

        Calendar gc = Calendar.getInstance();
        gc.setTime(date);
        int prefixDayLength = offsetExp.length();
        //判断是否是纯数字
        if (IS_NUMBER.matcher(offsetExp).matches()) {
            int days = Integer.parseInt(offsetExp);
            gc.add(Calendar.DAY_OF_MONTH, days);
        } else {
            String timeType = offsetExp.substring(prefixDayLength - 1, prefixDayLength);
            int offset = Integer.parseInt(offsetExp.substring(0, prefixDayLength - 1));
            switch (timeType) {
                case TIME_TYPE_D:
                    gc.add(Calendar.DAY_OF_MONTH, offset);
                    break;
                case TIME_TYPE_M:
                    gc.add(Calendar.MONTH, offset);
                    break;
                case TIME_TYPE_Y:
                    gc.add(Calendar.YEAR, offset);
                    break;
                case TIME_TYPE_MN:
                    gc.add(Calendar.MINUTE, offset);
                    break;
                case TIME_TYPE_H:
                    gc.add(Calendar.HOUR_OF_DAY, offset);
                    break;
                case TIME_TYPE_S:
                    gc.add(Calendar.SECOND, offset);
                    break;
                default:
            }
        }
        return gc.getTime();
    }

    /**
     * 根据日期结尾参数，如WS、WD、MS、MD、SS、SD、YS、YD来计算日期. 结果的精度与参数保持一致
     *
     * @param date      日期,默认格式 yyyyMMdd
     * @param suffixExp 日期结尾参数表达式
     * @return Date
     */
    private static Date calDateBySuffixExp(Date date, String suffixExp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        switch (suffixExp) {
            case DAY_FUNC_PARAM_WS:
                return beginOfWeek(calendar);
            case DAY_FUNC_PARAM_WD:
                return endOfWeek(calendar);
            case DAY_FUNC_PARAM_MS:
                return beginOfMonth(calendar);
            case DAY_FUNC_PARAM_MD:
                return endOfMonth(calendar);
            case DAY_FUNC_PARAM_SS:
                return beginOfQuarter(calendar);
            case DAY_FUNC_PARAM_SD:
                return endOfQuarter(calendar);
            case DAY_FUNC_PARAM_YS:
                return beginOfYear(calendar);
            case DAY_FUNC_PARAM_YD:
                return endOfYear(calendar);
            default:
                log.warn("日期结尾参数不支持:{}", suffixExp);
                return date;
        }
    }

    /**
     * 获取星期一.
     *
     * @param calendar 当前时间
     * @return 获取星期一
     */
    private static Date beginOfWeek(Calendar calendar) {
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取星期日.
     *
     * @param calendar 当前时间
     * @return 获取星期日
     */
    private static Date endOfWeek(Calendar calendar) {
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek() + 6);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 获取当月第一天.
     *
     * @param calendar 当前时间
     * @return 获取当月第一天
     */
    private static Date beginOfMonth(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当月最后一天.
     *
     * @param calendar 当前时间
     * @return 获取当月最后一天
     */
    private static Date endOfMonth(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 获取当前季度第一天.
     *
     * @param calendar 当前时间
     * @return 获取当前季度第一天
     */
    private static Date beginOfQuarter(Calendar calendar) {
        int month = calendar.get(Calendar.MONTH);
        int quarterStartMonth = ((month / 3) * 3) % 12;
        calendar.set(Calendar.MONTH, quarterStartMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当前季度最后一天.
     *
     * @param calendar 当前时间
     * @return 获取当前季度最后一天
     */
    private static Date endOfQuarter(Calendar calendar) {
        int month = calendar.get(Calendar.MONTH);
        int quarterStartMonth = ((month / 3) * 3) % 12;
        calendar.set(Calendar.MONTH, quarterStartMonth + 2);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 获取当前年第一天.
     *
     * @param calendar 当前时间
     * @return 获取当前年第一天
     */
    private static Date beginOfYear(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当前年最后一天.
     *
     * @param calendar 当前时间
     * @return 获取当前年最后一天
     */
    private static Date endOfYear(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }
    
    /**
     * 根据现有格式转换成另外的格式.
     *
     * @param dateStr    : 基础日期字符串
     * @param sourcePattern : 源日期格式
     * @param targetPattern : 目标日期格式
     * @return 格式化后的日期字符串
     */
    public static String convertPattern(String dateStr, String sourcePattern, String targetPattern) {
        Date date = DateUtil.parse(dateStr, sourcePattern);
        
        return DateUtil.format(date, targetPattern);
    }
}

package com.datafusion.common.cron;

import cn.hutool.core.util.ObjectUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期工具类.
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/18
 * @since 2018/11/18
 */
public class DateUtil {

    /**
     * 时间格式.
     */
    public static final String SDF_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /**
     * 时间格式.
     */
    public static final String SDF_DATETIME_SHORT = "yyyyMMddHHmmss";

    /**
     * 时间格式.
     */
    public static final String SDF_DATETIME_MS = "yyyyMMddHHmmssSSS";

    /**
     * 时间格式.
     */
    public static final String SDF_DATE = "yyyy-MM-dd";

    /**
     * 字符串转 long 型时间.
     *
     * @param dateStr 日期字符串
     * @param pattern 格式化字符串
     * @return long 型时间
     */
    public static long toLong(String dateStr, String pattern) {
        return toDate(dateStr, pattern).getTime();
    }

    /**
     * 字符串转 long 型时间. 默认输入格式 yyyy-MM-dd HH:mm:ss
     *
     * @param dateStr 日期字符串
     * @return long 型时间
     */
    public static long toLong(String dateStr) {
        return toDate(dateStr, null).getTime();
    }

    /**
     * 时间戳转字符串.
     *
     * @param timestamp 时间戳
     * @return 日期字符串
     */
    public static String timestampToDate(long timestamp) {
        return timestampToDate(timestamp, null);
    }

    /**
     * 时间戳转字符串.
     *
     * @param timestamp 时间戳
     * @param pattern   格式化字符串
     * @return 日期字符串
     */
    public static String timestampToDate(long timestamp, String pattern) {
        Date date = new Date(timestamp);
        if (null != pattern && !pattern.isEmpty()) {
            return new SimpleDateFormat(pattern).format(date);
        } else {
            return new SimpleDateFormat(SDF_DATETIME).format(date);
        }
    }

    /**
     * 字符串转日期.
     *
     * @param dateStr 日期字符串
     * @return 日期 yyyy-MM-dd HH:mm:ss
     */
    public static Date toDate(String dateStr) {
        return toDate(dateStr, null);
    }

    /**
     * 字符串转日期.
     *
     * @param dateStr 日期字符串
     * @param pattern 格式化字符串
     * @return 日期
     */
    public static Date toDate(String dateStr, String pattern) {
        try {
            if (null != pattern && !pattern.isEmpty()) {
                return new SimpleDateFormat(pattern).parse(dateStr);
            } else {
                return new SimpleDateFormat(SDF_DATETIME).parse(dateStr);
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 日期转字符串.
     *
     * @param date 日期
     * @return 字符串 yyyy-MM-dd HH:mm:ss
     */
    public static String toStr(Date date) {
        return toStr(date, SDF_DATETIME);
    }

    /**
     * 日期转字符串.
     *
     * @param date   日期
     * @param format 格式化字符串
     * @return 字符串
     */
    public static String toStr(Date date, String format) {
        SimpleDateFormat sdf;
        if (null != format && !format.isEmpty()) {
            sdf = new SimpleDateFormat(format);
            return sdf.format(date);
        } else {
            sdf = new SimpleDateFormat(SDF_DATETIME);
            return sdf.format(date);
        }
    }

    /**
     * 计算某一天是一个月的哪一天.
     *
     * @param date 日期
     * @return 1-31
     */
    public static int day(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return day(cal);
    }

    /**
     * 计算日期是一个月的哪一天.
     *
     * @param calendar 日期对象
     * @return 1-31
     */
    public static int day(Calendar calendar) {
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 计算某一天是星期几.
     *
     * @param date 日期
     * @return 星期几, 星期1是1, 星期天是0  0-6
     */
    public static int week(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return week(cal);
    }

    /**
     * 计算某一天是星期几.
     *
     * @param calendar 日期对象
     * @return 星期几, 星期1是1, 星期天是0  0-6
     */
    public static int week(Calendar calendar) {
        return calendar.get(Calendar.DAY_OF_WEEK) - 1;
    }

    /**
     * 计算某一天的月份.
     *
     * @param date 日期
     * @return 月份, 1开始
     */
    public static int month(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return month(cal);
    }

    /**
     * 计算某一天的月份.
     *
     * @param calendar 日期对象
     * @return 月份, 1开始
     */
    public static int month(Calendar calendar) {
        return calendar.get(Calendar.MONTH) + 1;
    }

    /**
     * 计算某一天的年.
     *
     * @param date 日期
     * @return 年
     */
    public static int year(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return year(cal);
    }

    /**
     * 计算某一天的年.
     *
     * @param calendar 日期对象
     * @return 年
     */
    public static int year(Calendar calendar) {
        return calendar.get(Calendar.YEAR);
    }

    /**
     * 指定时间增加若干小时.
     *
     * @param hour 小时
     * @param date 日期
     * @return 日期
     */
    public static Date hourAdd(int hour, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        // 24小时制
        cal.add(Calendar.HOUR, 1);
        return cal.getTime();
    }

    /**
     * 计算某一天的时.
     *
     * @param date 日期
     * @return 时 0-23
     */
    public static int hour(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return hour(cal);
    }

    /**
     * 计算某一天的时.
     *
     * @param calendar 日期对象
     * @return 时 0-23
     */
    public static int hour(Calendar calendar) {
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 计算某一天的分.
     *
     * @param date 日期
     * @return 分 0-59
     */
    public static int minute(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return minute(cal);
    }

    /**
     * 计算某一天的分.
     *
     * @param calendar 日期对象
     * @return 分 0-59
     */
    public static int minute(Calendar calendar) {
        return calendar.get(Calendar.MINUTE);
    }

    /**
     * 计算某一天的秒.
     *
     * @param date 日期
     * @return 秒 0-59
     */
    public static int second(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return second(cal);
    }

    /**
     * 计算某一天的秒.
     *
     * @param calendar 日期对象
     * @return 秒 0-59
     */
    public static int second(Calendar calendar) {
        return calendar.get(Calendar.SECOND);
    }

    /**
     * 计算两个时分秒时间的差距是否在给定的容忍范围内.
     *
     * @param one     比较的一方
     * @param two     比较的另外一方
     * @param seconds 容忍范围
     * @return true if in the range of seconds or false if out of the range
     */
    public static boolean equalsWithTolerance(TimeOfDay one, TimeOfDay two, Integer seconds) {
        //秒数为0退化为equals
        if (null == seconds || 0 == seconds) {
            return one.equals(two);
        }
        //秒数是否在给定的容忍范围内
        return distance(one, two) <= seconds;
    }

    /**
     * 计算两个时间的秒数差.
     *
     * @param one 时间1
     * @param two 时间2
     * @return (时间1 - 时间2)的绝对值 long型
     */
    public static long distance(TimeOfDay one, TimeOfDay two) {
        Calendar calendar1 = Calendar.getInstance();
        calendar1.set(Calendar.HOUR_OF_DAY, one.getHour());
        calendar1.set(Calendar.MINUTE, one.getMinute());
        calendar1.set(Calendar.SECOND, one.getSecond());

        Calendar calendar2 = Calendar.getInstance();
        calendar2.set(Calendar.HOUR_OF_DAY, two.getHour());
        calendar2.set(Calendar.MINUTE, two.getMinute());
        calendar2.set(Calendar.SECOND, two.getSecond());

        //秒数是否在给定的容忍范围内
        return Math.abs(calendar1.getTimeInMillis() / 1000 - calendar2.getTimeInMillis() / 1000);
    }

    /**
     * 判断时间戳是否是凌晨整点.
     *
     * @param timestamp 时间戳
     * @return true/false
     */
    public static boolean isMidnight(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        int hours = calendar.get(Calendar.HOUR_OF_DAY);
        int minutes = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);

        return hours == 0 && minutes == 0 && seconds == 0;
    }

    /**
     * 将UTC时间转换为上海时间.
     *
     * @param utcLocalDateTime UTC时间
     * @return 上海时间
     */

    public static LocalDateTime convertToShanghaiTime(LocalDateTime utcLocalDateTime) {
        if (ObjectUtil.isEmpty(utcLocalDateTime)) {
            return null;
        }
        // 创建表示零时区（UTC）的ZoneId
        ZoneId utcZoneId = ZoneId.of("UTC");
        // 将LocalDateTime与UTC时区结合，创建一个ZonedDateTime对象
        ZonedDateTime utcZonedDateTime = utcLocalDateTime.atZone(utcZoneId);
        // 获取东八区的ZoneId
        ZoneId shanghaiZoneId = ZoneId.of("Asia/Shanghai");
        // 使用withZoneSameInstant方法将UTC时间的ZonedDateTime转换为东八区时间的ZonedDateTime
        // 注意：withZoneSameInstant会保持瞬间时间（毫秒数）不变，仅转换时区
        ZonedDateTime shanghaiZonedDateTime =
                utcZonedDateTime.withZoneSameInstant(shanghaiZoneId);
        return shanghaiZonedDateTime.toLocalDateTime();
    }
}

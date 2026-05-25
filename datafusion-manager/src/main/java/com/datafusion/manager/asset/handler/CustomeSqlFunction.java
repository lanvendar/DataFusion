package com.datafusion.manager.asset.handler;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/29
 * @since 2025/10/29
 */
public class CustomeSqlFunction {
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object IF(Boolean isTrue, Object a, Object b) {
        return isTrue == true ? a : b;
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat(String... str) {
        return String.join("", str);
    }

    /**
     * concat 函数 - 支持混合类型的连接.
     * 用于解决 Calcite 无法处理 concat(CHARACTER, ANY, ...) 的问题
     *
     * @param args 要连接的对象数组
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat(Object... args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg == null ? "null" : String.valueOf(arg));
        }
        return sb.toString();
    }

    // 新增：支持两个参数
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat(Object obj1, Object obj2) {
        return String.valueOf(obj1) + String.valueOf(obj2);
    }

    // 新增：支持三个参数
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat(Object obj1, Object obj2, Object obj3) {
        return String.valueOf(obj1) + String.valueOf(obj2) + String.valueOf(obj3);
    }

    // 新增：支持四个参数
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat(Object obj1, Object obj2, Object obj3, Object obj4) {
        return String.valueOf(obj1) + String.valueOf(obj2) + String.valueOf(obj3) + String.valueOf(obj4);
    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String wm_concat(String... str) {
        return String.join("", str);
    }

    /**
     * wm_concat 聚合函数 - 带分隔符版本.
     * MySQL/Oracle 风格，用法: wm_concat(',', column)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param separator 分隔符
     * @param value 要连接的值
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String wm_concat(String separator, Object value) {
        return String.valueOf(value); // 仅用于类型推断
    }

    /**
     * wm_concat 聚合函数 - CHARACTER 版本（Calcite 字符串类型）.
     *
     * @param separator 分隔符
     * @param value 要连接的值
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String wm_concat(Object separator, Object value) {
        return String.valueOf(value); // 仅用于类型推断
    }

    /**
     * wm_concat 聚合函数 - 多参数版本.
     *
     * @param args 要连接的值
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String wm_concat(Object... args) {
        return String.valueOf(args[0]); // 仅用于类型推断
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer instr(String str, String subStr) {
        return str.contains(subStr) ? 1 : 0;
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String date_format(Date date, String format) {
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * date_format 函数 - Object 版本（通用类型）.
     * 用于处理 CALCITE 无法推断类型的情况
     *
     * @param dateValue 日期值
     * @param format 格式
     * @return 格式化后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String date_format(Object dateValue, String format) {
        return "1970-01-01"; // 仅用于类型推断
    }

    /**
     * date_format 函数 - 单参数版本（无格式，使用默认格式）.
     *
     * @param dateValue 日期值
     * @return 格式化后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String date_format(Object dateValue) {
        return "1970-01-01"; // 仅用于类型推断
    }
    
    /**
     * collect_list 聚合函数 - 将多行数据收集到数组中.
     * MaxCompute/Hive 函数，命令格式: array collect_list(&lt;colname&gt;)
     * 返回 ARRAY 类型。colname 值为 NULL 时，该行不参与计算。
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 要收集的列值
     * @return ARRAY 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static List<String> collect_list(String... args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return Stream.of(args)
                .filter(x -> x != null)
                .collect(Collectors.toList());
    }



    /**
     * collect_set 聚合函数 - 将多行数据收集到数组中并去重.
     * MaxCompute/Hive 函数，命令格式: array collect_set(&lt;colname&gt;)
     * 返回 ARRAY 类型。colname 值为 NULL 时，该行不参与计算。
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 要收集的列值
     * @return ARRAY 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Set<String> collect_set(String... args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return Stream.of(args)
                .filter(x -> x != null)
                .collect(Collectors.toSet());
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date date_sub(Date date, Integer day) {
        return date;
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date date_add(Date date, Integer day) {
        return date;
    }
    
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date date_add(Date date, Integer day, String str) {
        return date;
    }

    // ==================== DATEADD 函数 (MaxCompute风格) ====================

    /**
     * DATEADD 函数 - 根据指定的单位和数值，进行日期的增减运算.
     * MaxCompute 函数，命令格式: DATEADD(&lt;d&gt; DATE|TIMESTAMP|TIMESTAMPTZ, &lt;num&gt; BIGINT, &lt;str&gt; TEXT)
     * 默认支持的时间范围为 1925~2282年
     *
     * @param d 原始的日期或时间值
     * @param num 增加或减少的数量
     * @param str 指定的时间单位，包括：年（yyyy、year）、月（mm、month、mon）、日（dd、day）、时（hh、hour）、分（mi）、秒（ss）
     * @return 返回 DATE、TIMESTAMP、TIMESTAMPTZ 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Date DATEADD(Date d, Long num, String str) {
        return d; // 仅用于类型推断
    }

    /**
     * DATEADD 函数 - Integer 版本.
     *
     * @param d 原始的日期或时间值
     * @param num 增加或减少的数量
     * @param str 指定的时间单位
     * @return 返回 DATE、TIMESTAMP、TIMESTAMPTZ 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Date DATEADD(Date d, Integer num, String str) {
        return d; // 仅用于类型推断
    }

    /**
     * DATEADD 函数 - Number 版本.
     *
     * @param d 原始的日期或时间值
     * @param num 增加或减少的数量
     * @param str 指定的时间单位
     * @return 返回 DATE、TIMESTAMP、TIMESTAMPTZ 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Date DATEADD(Date d, Number num, String str) {
        return d; // 仅用于类型推断
    }

    /**
     * DATEADD 函数 - Object 版本（通用类型）.
     *
     * @param d 原始的日期或时间值
     * @param num 增加或减少的数量
     * @param str 指定的时间单位
     * @return 返回 DATE、TIMESTAMP、TIMESTAMPTZ 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Date DATEADD(Object d, Long num, String str) {
        return null; // 仅用于类型推断
    }

    /**
     * DATEADD 函数 - Object 版本（Integer 数量）.
     *
     * @param d 原始的日期或时间值
     * @param num 增加或减少的数量
     * @param str 指定的时间单位
     * @return 返回 DATE、TIMESTAMP、TIMESTAMPTZ 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Date DATEADD(Object d, Integer num, String str) {
        return null; // 仅用于类型推断
    }

    // ==================== dateadd 别名 (SQL Server风格) ====================

    /**
     * dateadd 函数 - SQL Server 风格的日期加法.
     * 用法: dateadd(day, 1, date) 或 dateadd(date, 1, 'day')
     *
     * @param date 日期
     * @param amount 增加的数量
     * @param unit 时间单位 (如 'day', 'month', 'year')
     * @return 返回 date
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date dateadd(Date date, Integer amount, String unit) {
        return date;
    }

    /**
     * dateadd 函数 - SQL Server 风格的三参数版本（参数顺序不同）.
     * 用法: dateadd('day', 1, date)
     *
     * @param unit 时间单位 (如 'day', 'month', 'year')
     * @param amount 增加的数量
     * @param date 日期
     * @return 返回 date
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date dateadd(String unit, Integer amount, Date date) {
        return date;
    }

    // 支持 NUMERIC 类型的重载
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date dateadd(Date date, Number amount, String unit) {
        return date;
    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date dateadd(String unit, Number amount, Date date) {
        return date;
    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date to_date(String format) {
        return Date.valueOf(format);
    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date to_date(String dateStr, String format) {
        try {
            return new Date(new SimpleDateFormat(format).parse(dateStr).getTime());
        } catch (Exception e) {
            return null;
        }

    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long unix_timestamp(String dateStr) {
        return System.currentTimeMillis();
    }

    /**
     * from_unixtime 函数 - 将时间戳转换为日期时间字符串.
     * MySQL/Hive 函数，用法: from_unixtime(timestamp) 或 from_unixtime(timestamp, format)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param timestamp 时间戳（秒或毫秒）
     * @return 日期时间字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String from_unixtime(Long timestamp) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * from_unixtime 函数 - 带格式版本.
     *
     * @param timestamp 时间戳
     * @param format 格式
     * @return 格式化后的日期时间字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String from_unixtime(Long timestamp, String format) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * from_unixtime 函数 - Integer 版本.
     *
     * @param timestamp 时间戳
     * @return 日期时间字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String from_unixtime(Integer timestamp) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * from_unixtime 函数 - Double 版本（支持任意数值类型）.
     *
     * @param timestamp 时间戳
     * @return 日期时间字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String from_unixtime(Double timestamp) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * from_unixtime 函数 - Object 版本（通用类型，接受任何类型）.
     *
     * @param timestamp 时间戳
     * @return 日期时间字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String from_unixtime(Object timestamp) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date date(String dateStr) {
        return Date.valueOf(dateStr);
    }

    /**
     * MINUTE 函数 - 从时间/日期中提取分钟数.
     * MySQL/Hive 函数，用法: MINUTE(date_column)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param dateValue 日期/时间值
     * @return 分钟数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer MINUTE(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * HOUR 函数 - 从时间/日期中提取小时数.
     * MySQL/Hive 函数，用法: HOUR(date_column)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param dateValue 日期/时间值
     * @return 小时数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer HOUR(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * SECOND 函数 - 从时间/日期中提取秒数.
     * MySQL/Hive 函数，用法: SECOND(date_column)
     *
     * @param dateValue 日期/时间值
     * @return 秒数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer SECOND(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * DAY 函数 - 从日期中提取天数.
     * MySQL/Hive 函数，用法: DAY(date_column)
     *
     * @param dateValue 日期值
     * @return 天数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer DAY(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * DAYOFMONTH 函数 - 从日期中提取一个月中的第几天.
     *
     * @param dateValue 日期值
     * @return 天数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer DAYOFMONTH(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * MONTH 函数 - 从日期中提取月份.
     * MySQL/Hive 函数，用法: MONTH(date_column)
     *
     * @param dateValue 日期值
     * @return 月份
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer MONTH(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * YEAR 函数 - 从日期中提取年份.
     * MySQL/Hive 函数，用法: YEAR(date_column)
     *
     * @param dateValue 日期值
     * @return 年份
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer YEAR(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * datetime 函数 - 将值转换为日期类型.
     * MySQL 函数，用法: datetime(expr)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param value 要转换的值
     * @return 日期
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date datetime(Object value) {
        return null; // 仅用于类型推断
    }

    /**
     * WEEK 函数 - 从日期中提取一年中的第几周.
     *
     * @param dateValue 日期值
     * @return 周数
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer WEEK(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * DAYOFWEEK 函数 - 从日期中提取一周中的第几天 (1-7).
     *
     * @param dateValue 日期值
     * @return 星期几
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer DAYOFWEEK(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * QUARTER 函数 - 从日期中提取季度 (1-4).
     *
     * @param dateValue 日期值
     * @return 季度
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static Integer QUARTER(Object dateValue) {
        return 0; // 仅用于类型推断
    }

    /**
     * datediff 函数 - 计算两个日期之间的天数差.
     * MySQL/Redshift 用法: datediff(date1, date2)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param date1 日期1
     * @param date2 日期2
     * @return 天数差
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer datediff(Object date1, Object date2) {
        return 0; // 仅用于类型推断
    }

    /**
     * datediff 函数 - 三个参数版本.
     * Redshift/SQL Server 风格: datediff(part, date1, date2)
     *
     * @param part 时间部分 (day, month, year 等)
     * @param date1 日期1
     * @param date2 日期2
     * @return 差值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer datediff(Object part, Object date1, Object date2) {
        return 0; // 仅用于类型推断
    }

    /**
     * date_diff 函数 - 计算两个日期之间的差值.
     * BigQuery/Snowflake 风格
     *
     * @param part 时间部分
     * @param date1 日期1
     * @param date2 日期2
     * @return 差值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer date_diff(Object part, Object date1, Object date2) {
        return 0; // 仅用于类型推断
    }

    /**
     * timestampdiff 函数 - 计算两个时间戳之间的差值.
     * MySQL 用法: timestampdiff(unit, date1, date2)
     *
     * @param unit 时间单位
     * @param date1 日期1
     * @param date2 日期2
     * @return 差值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer timestampdiff(Object unit, Object date1, Object date2) {
        return 0; // 仅用于类型推断
    }

    // ==================== 其他常用函数 ====================

    /**
     * coalesce 函数 - 返回第一个非NULL的值.
     * 标准SQL函数，用于Calcite类型推断.
     *
     * @param args 可变参数
     * @return 第一个非NULL的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object coalesce(Object... args) {
        for (Object arg : args) {
            if (arg != null) {
                return arg;
            }
        }
        return null;
    }

    /**
     * coalesce 函数 - 两个参数版本 (String, Timestamp).
     * 解决 COALESCE(String, TIMESTAMP) 类型推断问题
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @return 第一个非NULL的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object coalesce(Object arg1, Object arg2) {
        return arg1 != null ? arg1 : arg2;
    }

    /**
     * coalesce 函数 - 三个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @return 第一个非NULL的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object coalesce(Object arg1, Object arg2, Object arg3) {
        if (arg1 != null) {
            return arg1;
        }
        if (arg2 != null) {
            return arg2;
        }
        return arg3;
    }

    /**
     * coalesce 函数 - 四个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @param arg4 第四个值
     * @return 第一个非NULL的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object coalesce(Object arg1, Object arg2, Object arg3, Object arg4) {
        if (arg1 != null) return arg1;
        if (arg2 != null) return arg2;
        if (arg3 != null) return arg3;
        return arg4;
    }

    /**
     * coalesce 函数 - 五个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @param arg4 第四个值
     * @param arg5 第五个值
     * @return 第一个非NULL的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object coalesce(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (arg1 != null) {
            return arg1;
        }
        if (arg2 != null) {
            return arg2;
            // 仅用于类型推断
        }
        if (arg3 != null) {
            return arg3;
        }
        if (arg4 != null) {
            return arg4;
        }
        return arg5;
    }

    /**
     * abs 函数 - 返回绝对值.
     *
     * @param value 数值
     * @return 绝对值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Number abs(Number value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return Math.abs(value.doubleValue());
        } else if (value instanceof Float) {
            return Math.abs(value.floatValue());
        } else if (value instanceof Long) {
            return Math.abs(value.longValue());
        } else {
            return Math.abs(value.intValue());
        }
    }

    /**
     * abs 函数 - Integer版本.
     *
     * @param value 整数值
     * @return 绝对值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Integer abs(Integer value) {
        return value == null ? null : Math.abs(value);
    }

    /**
     * abs 函数 - Long版本.
     *
     * @param value 长整数值
     * @return 绝对值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long abs(Long value) {
        return value == null ? null : Math.abs(value);
    }

    /**
     * abs 函数 - Double版本.
     *
     * @param value 双精度值
     * @return 绝对值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Double abs(Double value) {
        return value == null ? null : Math.abs(value);
    }

    // ==================== 聚合函数 (Aggregate Functions) ====================

    /**
     * max_by 聚合函数 - 返回 compareValue 最大时对应的 value.
     * 支持时间类型比较，如 max_by(t1.offset_data_value, t1.update_time).
     * 实际聚合逻辑由数据库执行，此方法仅用于 Calcite 类型推断.
     *
     * @param value 要返回的值（任意类型）
     * @param compareValue 用于比较的值（支持时间类型：Timestamp, Date等）
     * @return 返回 value
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object max_by(Object value, Object compareValue) {
        // 此方法仅用于 Calcite 解析时的类型推断
        // 实际执行由数据库的 max_by 聚合函数完成
        return value;
    }

    /**
     * LAST_VALUE 窗口函数 - 返回窗口范围内的最后一个值.
     * 支持两参数版本：LAST_VALUE(expr, ignoreNulls) 其中 ignoreNulls 为布尔值
     * 用于支持 IGNORE NULLS 语义
     * 实际聚合逻辑由数据库执行，此方法仅用于 Calcite 类型推断.
     *
     * @param value 要返回的值（任意类型）
     * @param ignoreNulls 是否忽略 NULL 值（true=忽略，false=不忽略）
     * @return 返回 value
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object last_value(Object value, Boolean ignoreNulls) {
        return value;
    }

    /**
     * string_agg 聚合函数 - 将多行字符串连接成一个字符串.
     * 类似于 GROUP_CONCAT，用法: string_agg(column, ',')
     * 实际聚合逻辑由数据库执行，此方法仅用于 Calcite 类型推断.
     *
     * @param value 要连接的值
     * @param separator 分隔符
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String string_agg(Object value, String separator) {
        return String.valueOf(value);
    }

    /**
     * min_by 聚合函数 - 返回 compareValue 最小时对应的 value.
     * 支持任意可比较类型.
     * 实际聚合逻辑由数据库执行，此方法仅用于 Calcite 类型推断.
     *
     * @param value 要返回的值（任意类型）
     * @param compareValue 用于比较的值
     * @return 返回 value
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object min_by(Object value, Object compareValue) {
        return value;
    }

    /**
     * FIRST_VALUE 窗口函数 - 返回窗口范围内的第一个值.
     * 支持两参数版本：FIRST_VALUE(expr, ignoreNulls) 其中 ignoreNulls 为布尔值
     * 用于支持 IGNORE NULLS 语义
     * 实际聚合逻辑由数据库执行，此方法仅用于 Calcite 类型推断.
     *
     * @param value 要返回的值（任意类型）
     * @param ignoreNulls 是否忽略 NULL 值（true=忽略，false=不忽略）
     * @return 返回 value
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object first_value(Object value, Boolean ignoreNulls) {
        return value;
    }

    /**
     * to_char 函数 - 将日期转换为指定格式的字符串.
     * PostgreSQL/Oracle 风格，用法: to_char(date, 'yyyy-MM-dd')
     *
     * @param date 日期
     * @param format 格式模板
     * @return 格式化后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String to_char(java.util.Date date, String format) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * to_char 函数 - 将sql.Date日期转换为指定格式的字符串.
     * 用于 CAST(... AS date) 的结果
     *
     * @param date sql.Date日期
     * @param format 格式模板
     * @return 格式化后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String to_char(Date date, String format) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * to_char 函数 - 将字符串转换为指定格式的字符串（重载）.
     * 用于 to_char('2024-01-01', 'yyyy-MM-dd')
     *
     * @param dateStr 日期字符串
     * @param format 格式模板
     * @return 格式化后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String to_char(String dateStr, String format) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            // 尝试解析日期字符串
            java.util.Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(dateStr);
            return new SimpleDateFormat(format).format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    /**
     * TO_CHAR 函数 - 将时间戳转换为字符串.
     * MaxCompute 函数，用法: TO_CHAR(TIMESTAMP|TIMESTAMPTZ, TEXT)
     * 默认支持的时间范围为 1925~2282年
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param timestamp 时间戳
     * @param format 格式
     * @return TEXT 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static String TO_CHAR(Object timestamp, String format) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * TO_CHAR 函数 - 单参数版本（使用默认格式）.
     *
     * @param timestamp 时间戳
     * @return TEXT 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:AbbreviationAsWordInName"})
    public static String TO_CHAR(Object timestamp) {
        return "1970-01-01 00:00:00"; // 仅用于类型推断
    }

    /**
     * LAST_DAY 函数 - 返回月份的最后一天.
     * MySQL/BigQuery 函数，用法: LAST_DAY(date)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param dateStr 日期字符串
     * @return 月份最后一天的日期
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date last_day(String dateStr) {
        return null; // 仅用于类型推断
    }

    /**
     * LAST_DAY 函数 - Date 类型版本.
     *
     * @param date 日期
     * @return 月份最后一天的日期
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date last_day(Date date) {
        return date; // 仅用于类型推断
    }

    // ==================== DATETRUNC 函数 (MaxCompute风格) ====================

    /**
     * DATETRUNC 函数 - 截断日期到指定单位.
     * MaxCompute 函数，用法: datetrunc(date, 'day') 或 datetrunc(timestamp, 'month')
     * 返回截断后的日期/时间
     *
     * @param date 日期或时间戳
     * @param format 截断单位 ('year', 'month', 'day', 'hour', 'minute', 'second')
     * @return 截断后的日期/时间
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object datetrunc(Object date, String format) {
        return date; // 仅用于类型推断
    }

    /**
     * DATETRUNC 函数 - Date 版本.
     *
     * @param date 日期
     * @param format 截断单位
     * @return 截断后的日期
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Date datetrunc(Date date, String format) {
        return date; // 仅用于类型推断
    }

    /**
     * SUBSTR 函数 - 字符串截取.
     * 用法: SUBSTR(string, start, length)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param str 原始字符串
     * @param start 起始位置
     * @param length 截取长度
     * @return 截取后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String substr(String str, Integer start, Integer length) {
        return str; // 仅用于类型推断
    }

    /**
     * SUBSTR 函数 - 两个参数版本.
     * 用法: SUBSTR(string, start)
     *
     * @param str 原始字符串
     * @param start 起始位置
     * @return 截取后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String substr(String str, Integer start) {
        return str; // 仅用于类型推断
    }

    /**
     * CONCAT_WS 函数 - 使用分隔符连接字符串.
     * 用法: CONCAT_WS(separator, str1, str2, ...)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param separator 分隔符
     * @param args 要连接的字符串数组
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat_ws(String separator, String... args) {
        return separator; // 仅用于类型推断
    }

    /**
     * CONCAT_WS 函数 - 数组版本.
     * 用法: CONCAT_WS(separator, array)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param separator 分隔符
     * @param strList 字符串数组
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat_ws(String separator, List<String> strList) {
        return separator; // 仅用于类型推断
    }

    /**
     * CONCAT_WS 函数 - Object 数组版本.
     * @param separator 分隔符
     * @param array 任意类型的数组
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat_ws(Object separator, List<?> array) {
        return String.valueOf(separator); // 仅用于类型推断
    }

    /**
     * CONCAT_WS 函数 - 原始类型数组版本.
     * @param separator 分隔符
     * @param array 数组
     * @return 连接后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String concat_ws_array(Object separator, List<?> array) {
        return String.valueOf(separator); // 仅用于类型推断
    }

    /**
     * collect_set 聚合函数 - 将多行数据收集到数组中并去重.
     * MaxCompute/Hive 函数，命令格式: array collect_set(&lt;colname&gt;)
     * 返回 ARRAY 类型。colname 值为 NULL 时，该行不参与计算。
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 要收集的值
     * @return ARRAY 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:OverloadMethodsDeclarationOrder"})
    public static List<Object> collect_set(Object... args) {
        return new java.util.ArrayList<>(); // 仅用于类型推断
    }

    /**
     * collect_list 聚合函数 - 将多行数据收集到数组中.
     * MaxCompute/Hive 函数，命令格式: array collect_list(&lt;colname&gt;)
     * 返回 ARRAY 类型。colname 值为 NULL 时，该行不参与计算。
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 要收集的值
     * @return ARRAY 类型
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:OverloadMethodsDeclarationOrder"})
    public static List<Object> collect_list(Object... args) {
        return new java.util.ArrayList<>(); // 仅用于类型推断
    }

    /**
     * SPLIT 函数 - 字符串分割.
     * 用法: SPLIT(str, delimiter)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param str 原始字符串
     * @param delimiter 分隔符
     * @return 分割后的数组
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static List<String> split(String str, String delimiter) {
        return null; // 仅用于类型推断
    }

    /**
     * REGEXP_REPLACE 函数 - 正则表达式替换.
     * 用法: REGEXP_REPLACE(str, regexp, replacement)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param str 原始字符串
     * @param regexp 正则表达式
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static String regexp_replace(String str, String regexp, String replacement) {
        return str; // 仅用于类型推断
    }

    /**
     * TRUNC 函数 - 截断数值到指定小数位数.
     * PostgreSQL/Oracle 函数，用法: TRUNC(number, decimal_places)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param number 要截断的数值
     * @param decimalPlaces 小数位数
     * @return 截断后的数值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Double trunc(Double number, Integer decimalPlaces) {
        return number; // 仅用于类型推断
    }

    /**
     * TRUNC 函数 - 截断数值（单参数版本）.
     * 用法: TRUNC(number)
     *
     * @param number 要截断的数值
     * @return 截断后的数值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Double trunc(Double number) {
        return number; // 仅用于类型推断
    }

    /**
     * TRUNC 函数 - Integer 版本.
     *
     * @param number 要截断的数值
     * @param decimalPlaces 小数位数
     * @return 截断后的数值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Double trunc(Integer number, Integer decimalPlaces) {
        return number == null ? null : number.doubleValue();
    }

    /**
     * TRUNC 函数 - Long 版本.
     *
     * @param number 要截断的数值
     * @param decimalPlaces 小数位数
     * @return 截断后的数值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Double trunc(Long number, Integer decimalPlaces) {
        return number == null ? null : number.doubleValue();
    }

    /**
     * TO_MILLIS 函数 - 将日期转换为毫秒时间戳.
     * 用法: to_millis(date_column)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param date 日期（支持多种类型）
     * @return 毫秒时间戳
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long to_millis(Object date) {
        return date == null ? null : System.currentTimeMillis(); // 仅用于类型推断
    }

    /**
     * TO_MILLIS 函数 - String 版本.
     *
     * @param dateStr 日期字符串
     * @return 毫秒时间戳
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long to_millis(String dateStr) {
        return System.currentTimeMillis(); // 仅用于类型推断
    }

    /**
     * TO_MILLIS 函数 - Date 版本.
     *
     * @param date java.sql.Date
     * @return 毫秒时间戳
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long to_millis(Date date) {
        return date == null ? null : date.getTime(); // 仅用于类型推断
    }

    /**
     * TO_MILLIS 函数 - Timestamp 版本.
     *
     * @param timestamp 时间戳
     * @return 毫秒时间戳
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Long to_millis(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.getTime(); // 仅用于类型推断
    }

    /**
     * sort_array 函数 - 对数组进行排序.
     * Spark/Hive 函数，用法: sort_array(array_col) 或 sort_array(array_col, true/false)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param array 数组
     * @return 排序后的数组
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static List<Object> sort_array(List<Object> array) {
        return array; // 仅用于类型推断
    }

    /**
     * sort_array 函数 - 对数组进行排序（带升序/降序参数）.
     *
     * @param array 数组
     * @param isAsc 是否升序 (true=升序, false=降序)
     * @return 排序后的数组
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static List<Object> sort_array(List<Object> array, Boolean isAsc) {
        return array; // 仅用于类型推断
    }

    // ==================== LEAST 和 GREATEST 函数 ====================

    /**
     * LEAST 函数 - 返回参数中的最小值.
     * MySQL/SQL Server 函数，用法: LEAST(a, b, c, ...)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 可变参数
     * @return 最小值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object least(Object... args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return args[0];
    }

    /**
     * LEAST 函数 - 两个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @return 最小值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object least(Object arg1, Object arg2) {
        return arg1;
    }

    /**
     * LEAST 函数 - 三个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @return 最小值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object least(Object arg1, Object arg2, Object arg3) {
        return arg1;
    }

    /**
     * LEAST 函数 - 四个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @param arg4 第四个值
     * @return 最小值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object least(Object arg1, Object arg2, Object arg3, Object arg4) {
        return arg1;
    }

    /**
     * GREATEST 函数 - 返回参数中的最大值.
     * MySQL/SQL Server 函数，用法: GREATEST(a, b, c, ...)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 可变参数
     * @return 最大值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object greatest(Object... args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return args[0];
    }

    /**
     * GREATEST 函数 - 两个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @return 最大值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object greatest(Object arg1, Object arg2) {
        return arg1;
    }

    /**
     * GREATEST 函数 - 三个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @return 最大值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object greatest(Object arg1, Object arg2, Object arg3) {
        return arg1;
    }

    /**
     * GREATEST 函数 - 四个参数版本.
     *
     * @param arg1 第一个值
     * @param arg2 第二个值
     * @param arg3 第三个值
     * @param arg4 第四个值
     * @return 最大值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object greatest(Object arg1, Object arg2, Object arg3, Object arg4) {
        return arg1;
    }

    // ==================== JSON 函数 ====================

    /**
     * json_build_object 函数 - 从键值对构建 JSON 对象.
     * PostgreSQL 函数，用法: json_build_object('key1', value1, 'key2', value2, ...)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param args 键值对参数 (key1, value1, key2, value2, ...)
     * @return JSON 对象字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object json_build_object(Object... args) {
        return new Object(); // 仅用于类型推断，返回 Object 以避免类型转换问题
    }

    /**
     * json_build_object 函数 - 两个参数版本 (key, value).
     *
     * @param key 键
     * @param value 值
     * @return JSON 对象字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object json_build_object(Object key, Object value) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * json_build_object 函数 - 四个参数版本.
     * json_array_elements 函数 - 展开 JSON 数组.
     * PostgreSQL 函数，用法: json_array_elements(json_array)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param jsonArray JSON 数组字符串
     * @return JSON 元素
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object json_array_elements(Object jsonArray) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * json_extract_path 函数 - 从 JSON 中提取指定路径的值.
     * PostgreSQL 函数，用法: json_extract_path(json, 'key1', 'key2', ...)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param json JSON 对象
     * @param pathParts 路径部分
     * @return 提取的值
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object json_extract_path(Object json, Object... pathParts) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * to_json 函数 - 将值转换为 JSON.
     * PostgreSQL 函数，用法: to_json(value)
     *
     * @param value 要转换的值
     * @return JSON 字符串
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object to_json(Object value) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * json_agg 聚合函数 - 将值聚合成 JSON 数组.
     * PostgreSQL 聚合函数，用法: json_agg(expression)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param value 要聚合的值
     * @return JSON 数组
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object json_agg(Object value) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * json_agg 聚合函数 - 带 ORDER BY 版本.
     * PostgreSQL 聚合函数，用法: json_agg(expression ORDER BY col)
     *
     * @return JSON 数组
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod", "checkstyle:JavadocMethod"})
    public static Object json_agg(Object... args) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * jsonb_array_elements 函数 - 展开 JSONB 数组元素.
     * PostgreSQL 函数，用法: jsonb_array_elements(jsonb_array)
     * 将 JSONB 数组拆成多行（返回多行）
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param jsonbArray JSONB 数组
     * @return 数组元素
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object jsonb_array_elements(Object jsonbArray) {
        return new Object(); // 仅用于类型推断
    }

    /**
     * tptodatetime 函数 - 将 TP 日期时间转换为标准日期时间格式.
     * 用法: tptodatetime(tp_date, tp_time)
     * 对于血缘解析，我们只关心参数的来源，不关心返回值
     *
     * @param tpDate TP 日期
     * @param tpTime TP 时间
     * @return 日期时间
     */
    @SuppressWarnings({"checkstyle:MethodName", "checkstyle:MissingJavadocMethod"})
    public static Object tptodatetime(Object tpDate, Object tpTime) {
        return new Object(); // 仅用于类型推断
    }

}


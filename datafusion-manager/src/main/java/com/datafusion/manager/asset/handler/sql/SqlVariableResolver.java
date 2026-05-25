package com.datafusion.manager.asset.handler.sql;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 变量替换.
 * @author xufeng
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
public class SqlVariableResolver {

    /**
     *  替换 SQL 中的动态时间变量 遵循 DolphinScheduler 常用变量格式.
     * @param sql sql
     * @return String
     */
    public static String replaceSqlVariables(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        LocalDate now = LocalDate.now();

        // 1. $[yyyy-1] : 去年 (如果你的逻辑是去年)
        // 注意：原代码里写的是 calendar.get(Calendar.YEAR)，即当前年
        // 这里按照变量名含义，通常建议是去年，如果你要今年，请把 .minusYears(1) 去掉
        String lastYear = String.valueOf(now.minusYears(1).getYear());
        sql = sql.replace("$[yyyy-1]", lastYear);

        // 2. $[yyyy-mm-dd-1] : 昨天
        String yesterday = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        sql = sql.replace("$[yyyy-mm-dd-1]", yesterday);

        // 3. $[yyyymm] : 当前年月 (格式: yyyyMM)
        // 原代码使用了 YEAR + "_" + MONTH，这里保持这种格式但修复了月份从0开始的问题
        String currentMonthStr = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[yyyymm]", currentMonthStr);

        // 4. $[add_months(yyyymm,-1)] : 上个月
        String lastMonthStr = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[add_months(yyyymm,-1)]", lastMonthStr);

        // 5. $[add_months(yyyymm,-2)] : 上上个月
        String lastTwoMonthStr = now.minusMonths(2).format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[add_months(yyyymm,-2)]", lastTwoMonthStr);

        // 6. $[yyyymm] : 当前年月 (格式: yyyyMM)
        // 原代码使用了 YEAR + "_" + MONTH，这里保持这种格式但修复了月份从0开始的问题
        String currentMonthStr1 = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[yyyyMM]", currentMonthStr1);

        // 7. $[add_months(yyyymm,-1)] : 上个月
        String lastMonthStr1 = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[add_months(yyyyMM,-1)]", lastMonthStr1);

        // 8. $[add_months(yyyymm,-2)] : 上上个月
        String lastTwoMonthStr1 = now.minusMonths(2).format(DateTimeFormatter.ofPattern("yyyyMM"));
        sql = sql.replace("$[add_months(yyyyMM,-2)]", lastTwoMonthStr1);

        // 6. $[yyyymm] : 当前年月 (格式: yyyyMM)
        // 原代码使用了 YEAR + "_" + MONTH，这里保持这种格式但修复了月份从0开始的问题
        String currentMonthStr2 = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
        //sql = sql.replace("$[yyyyMM-1]", currentMonthStr2);
        sql = sql.replaceAll("(?i)\\$\\[yyyyMM-1\\]", currentMonthStr2);

        return sql;
    }

    /**
     * main方法.
     * @param args 入参
     */
    public static void main(String[] args) {
        String testSql = "SELECT * FROM table WHERE dt = '$[yyyy-mm-dd-1]' AND month = '$[yyyymm]' AND last_m = '$[add_months(yyyymm,-1)]';";

        System.out.println("原始SQL: " + testSql);
        System.out.println("替换后: " + replaceSqlVariables(testSql));
    }
}

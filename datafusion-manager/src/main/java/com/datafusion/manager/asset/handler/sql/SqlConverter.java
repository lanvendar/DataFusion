package com.datafusion.manager.asset.handler.sql;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * sql转换.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/4/2
 * @since 2026/4/2
 */
@Slf4j
public class SqlConverter {

    /**
     * 转换 ATTACH PARTITION SQL.
     *
     * @param sql 原始 SQL
     * @return 转换后的 SQL
     */
    public static String convertSqlAttachParttion(String sql) {
        // 1. (?i) 忽略大小写
        // 2. (?m) 开启多行模式（可选）
        // 3. 末尾使用 [^;]+ 匹配直到遇到分号，确保能匹配多次而不越界
        String regex = "(?is)ALTER\\s+TABLE\\s+([\\w\\.]+)\\s+ATTACH\\s+PARTITION\\s+([\\w\\.]+)\\s+FOR\\s+VALUES\\s+IN\\s*\\(.*?\\)";
        // 替换模板：$1 和 $2 分别代表第一个和第二个括号捕获的内容
        String replacement = "ALTER TABLE $2 RENAME TO $1";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(sql);

        // 使用 replaceAll 一次性替换所有匹配项
        return matcher.replaceAll(replacement);
    }

    /**
     * 替换 SQL 中的表名.
     *
     * @param sql 原始 SQL
     * @param sourceTableName 原表名
     * @param targetTableName 目标表名
     * @return 替换后的 SQL
     */
    public static String replaceSql(String sql, String sourceTableName, String targetTableName) {
        sql = sql.replaceAll("\\b" + sourceTableName + "\\b", targetTableName);
        return sql;
    }

    /**
     * 运行 SQL 转换示例.
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        String sql3 = "ALTER TABLE dw.table1 ATTACH PARTITION dw.p1 FOR VALUES IN ('day'); \n"
                + "i lpve"
                + "ALTER TABLE dw.table3 ATTACH PARTITION dw.p2 FOR VALUES IN ('day'); \n"
                + " sha "
                + "ALTER   TaBLE   dw.sebu1_ads_user_day_data_up_grid ATTACH   PARTITION "
                + "dw.day_sebu1_ads_user_day_data_up_grid FOR VALUES IN ('day');\n";
        String sql2 = "hello world";

        String sql1 = sql2 + "alter table dw.sebu1_dws_device_5min_data \n"
                + "attach partition dw.sebu1_dws_device_5min_data_202604 \n"
                + "for values in('202604'\n"
                + ");" + sql3;
        String sql = "INSERT INTO dw.sebu1_ads_user_total_data\n"
                + "SELECT *\n"
                + "FROM tmp.tmp_day_sebu1_ads_user_total_data_03";

        //String result = convertSqlAttachParttion(sql);
        String result = replaceSql(sql, "day_sebu1_ads_user_total_data", "dw.sebu1_ads_user_total_data");

        log.info("原始 SQL: {}", sql);
        log.info("替换后 SQL: {}", result);
    }
}

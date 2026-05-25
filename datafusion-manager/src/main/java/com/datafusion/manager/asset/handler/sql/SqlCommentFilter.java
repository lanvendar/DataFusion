package com.datafusion.manager.asset.handler.sql;

/**
 * 优化后的 SQL 注释过滤器.
 * 能够准确识别单引号字符串，防止误删字符串内的连字符
 * @author xufeng
 * @version 1.0.0, 2026/2/26
 * @since 2026/2/26
 */
public class SqlCommentFilter {

    /**
     * 过滤 SQL 中的单行注释 (-- 及其后面的内容)，同时保留字符串内的连字符.
     *
     * @param sql 原始 SQL
     * @return 过滤后的 SQL
     */
    public static String filterSingleLineComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        StringBuilder sb = new StringBuilder();
        boolean inString = false; // 是否在单引号字符串内
        boolean inComment = false; // 是否在注释内
        char[] chars = sql.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = (i + 1 < chars.length) ? chars[i + 1] : '\0';

            // 1. 如果当前在注释中
            if (inComment) {
                // 遇到换行符，注释结束
                if (c == '\n' || c == '\r') {
                    inComment = false;
                    sb.append(c); // 保留换行符，维持 SQL 行号不变
                }
                continue; // 注释内容不放入 StringBuilder
            }

            // 2. 如果当前在字符串中
            if (inString) {
                sb.append(c);
                // 遇到单引号，可能是字符串结束，也可能是转义引点 ('')
                if (c == '\'') {
                    // 处理 SQL 标准中的转义：两个单引号表示一个单引号字符
                    if (next == '\'') {
                        sb.append(next);
                        i++; // 跳过下一个引号
                    } else {
                        inString = false; // 字符串真正结束
                    }
                }
                continue;
            }

            // 3. 正常 SQL 内容区域
            if (c == '-' && next == '-') {
                // 发现注释开始标记
                inComment = true;
                i++; // 跳过第二个 '-'
            } else {
                if (c == '\'') {
                    inString = true;
                }
                sb.append(c);
            }
        }

        return sb.toString();
    }

}

package com.datafusion.manager.asset.handler;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLResetStatement;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.druid.sql.ast.statement.SQLAlterTableRename;
import com.alibaba.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLAnalyzeTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.druid.sql.ast.statement.SQLCommentStatement;
import com.alibaba.druid.sql.ast.statement.SQLCommitStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLSetStatement;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.hive.visitor.HiveSchemaStatVisitor;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGEndTransactionStatement;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGStartTransactionStatement;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGSchemaStatVisitor;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.manager.asset.constant.AssetLineageConstant;
import com.datafusion.manager.asset.handler.sql.SqlCommentFilter;
import com.datafusion.manager.asset.handler.sql.SqlConverter;
import com.datafusion.manager.asset.handler.sql.SqlVariableResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * sql解析前,进行标准化,对calcite不支持的语法进行转换.
 * 包括将INSERT INTO ... WITH转换为内联子查询的形式.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/15
 * @since 2025/10/15
 */

@Slf4j
public class SqlDruidHandler {


    /**
     * 递归遍历解析SQL语句，收集对血缘解析有用的SQL.
     *
     * @param sql    原始SQL字符串
     * @param dbType 数据库类型
     * @return 返回sql list
     */
    public static List<String> preParseRecursive(String sql, String dbType) {
        List<String> sqlResults = new ArrayList<>();
        // 先进行去空格并转为小写
        String tmpSql = sql.trim().toLowerCase();
        if (StringUtils.isEmpty(tmpSql)) {
            return sqlResults;
        }

        //过滤单行注释
        tmpSql = SqlCommentFilter.filterSingleLineComments(tmpSql);

        DbType databaseType = getDbType(dbType);
        if (databaseType == null) {
            return sqlResults;
        }

        // 预处理：转换INSERT INTO ... WITH为标准WITH ... INSERT INTO
        //tmpSql = convertInsertWithCte(tmpSql);

        //变量替换
        tmpSql = SqlVariableResolver.replaceSqlVariables(tmpSql);

        //预处理 SQL，过滤掉不支持的语句
        tmpSql = preprocessUnsupportedStatements(tmpSql, databaseType);

        //sql语法转换
        tmpSql = SqlConverter.convertSqlAttachParttion(tmpSql);

        log.info("预处理后的sql:" + tmpSql);
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(tmpSql, databaseType);
        log.info("数量" + sqlStatements.size());
        for (SQLStatement sqlStatement : sqlStatements) {
            processSingleStatement(sqlStatement, databaseType, sqlResults);
        }
        log.info("处理后的sql集合:" + sqlResults);
        return sqlResults.stream().map(x -> {
            if (x.endsWith(";")) {
                return x.substring(0, x.length() - 1);
            }
            return x;
        }).collect(Collectors.toList());
    }

    /**
     * 预处理不支持的SQL语句，将其注释掉或移除.
     *
     * @param sql SQL字符串
     * @param databaseType 数据库类型
     * @return 处理后的SQL
     */

    private static String preprocessUnsupportedStatements(String sql, DbType databaseType) {
        if (databaseType == JdbcConstants.POSTGRESQL) {
            // 过滤掉包含 hg_computing_resource 的行（如 hg_computing_resource = 'serverless';）
            // (?m) 开启多行模式，使 ^ 匹配每行的开头
            sql = sql.replaceAll("(?mi)^[^;]*hg_computing_resource[^;]*;[\\r\\n]*", "");
            // 过滤掉包含 hg_experimental_enable_create_table_like_properties 的行
            sql = sql.replaceAll("(?mi)^[^;]*hg_experimental_enable_create_table_like_properties[^;]*;[\\r\\n]*", "");

            // 处理 dollar-quoted string ($$...$$) 语法，转换为单引号字符串
            // 这是一个简单的替换，只处理 $$...$$ 格式，不处理 $tag$...$tag$ 格式
            sql = sql.replaceAll("\\$\\$([^$]*)\\$\\$", "'$1'");

            // 正则解释：
            // (?i)         : 忽略大小写匹配
            // insert       : 匹配单词 insert
            // \\s+         : 匹配中间的一个或多个空格、换行或制表符
            // overwrite    : 匹配单词 overwrite
            sql = sql.replaceAll("(?i)insert\\s+overwrite", "insert into");
        }
        return sql;
    }

    /**
     * 处理单个SQLStatement，并将其转换或添加到结果列表中.
     * 这是一个递归的辅助方法，用于处理嵌套的语句或复杂情况。
     *
     * @param sqlStatement 当前需要处理的SQL语句
     * @param databaseType 数据库类型
     * @param sqlResults   收集转换后SQL的列表
     */
    private static void processSingleStatement(SQLStatement sqlStatement, DbType databaseType, List<String> sqlResults) {
        if (sqlStatement == null) {
            return;
        }

        // 排除掉不关心血缘的语句
        if (sqlStatement instanceof PGStartTransactionStatement
                || sqlStatement instanceof PGEndTransactionStatement
                || sqlStatement instanceof SQLCommentStatement
                || sqlStatement instanceof SQLDropStatement
                || sqlStatement instanceof SQLCommitStatement
                || sqlStatement instanceof SQLSetStatement
                || sqlStatement instanceof SQLTruncateStatement
                || sqlStatement instanceof SQLDeleteStatement
                || sqlStatement instanceof SQLAnalyzeTableStatement
                || sqlStatement instanceof SQLResetStatement
        ) {
            return;
        }

        if (sqlStatement instanceof SQLInsertStatement) {
            log.info("处理insert语句");
            SQLInsertStatement insertStatement = (SQLInsertStatement) sqlStatement;
            // insert overwrite 或 包含partition 的需要进行转换，统一为 insert into select
            if (insertStatement.isOverwrite() || CollectionUtils.isNotEmpty(insertStatement.getPartitions())) {
                String targetTableName = removeQuotation(insertStatement.getTableName().toString());
                if (insertStatement.getQuery() != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("insert into ").append(targetTableName).append(" ")
                            .append(SQLUtils.toSQLString(insertStatement.getQuery(), databaseType));
                    sqlResults.add(sb.toString());
                }
            } else {
                // 普通的insert，直接加入
                sqlResults.add(SQLUtils.toSQLString(insertStatement, databaseType));
            }
        } else if (sqlStatement instanceof SQLWithSubqueryClause) {
            // 其他类型的语句，默认直接加入，或者根据需要进行过滤
            sqlResults.add(SQLUtils.toSQLString(sqlStatement, databaseType));
        } else if (sqlStatement instanceof SQLCreateTableStatement) {
            SQLCreateTableStatement createTableStatement = (SQLCreateTableStatement) sqlStatement;
            // create table as select 转化为 insert into select
            if (createTableStatement.getSelect() != null) {
                String targetTableName = removeQuotation(createTableStatement.getTableSource().getExpr().toString());
                SQLSelect select = createTableStatement.getSelect();
                if (select.getWithSubQuery() != null) {
                    // 有 WITH 子句，使用完善的 CTE 展开方案
                    String resultSql = expandCteInCreateTable(select, targetTableName);
                    sqlResults.add(resultSql);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("insert into ").append(targetTableName).append(" ")
                            .append(SQLUtils.toSQLString(createTableStatement.getSelect(), databaseType));
                    sqlResults.add(sb.toString());
                }
            }
            // 如果 create table 不包含 select，且我们不关心创建表本身的血缘，则可以忽略。
            // 如果需要保留，则添加：sqlResults.add(SQLUtils.toSQLString(createTableStatement, databaseType));
        } else if (sqlStatement instanceof SQLCallStatement) {
            SQLCallStatement callStatement = (SQLCallStatement) sqlStatement;
            // 特殊处理Hologres的create table like函数
            if (AssetLineageConstant.HG_CREATE_TABLE_LIKE.equalsIgnoreCase(callStatement.getProcedureName().toString())) {
                if (callStatement.getParameters() != null && callStatement.getParameters().size() >= 2) {
                    String targetTableName = removeQuotation(callStatement.getParameters().get(0).toString());
                    String selectExpr = removeQuotation(callStatement.getParameters().get(1).toString());
                    StringBuilder sb = new StringBuilder();
                    sb.append("insert into ").append(targetTableName).append(" ").append(selectExpr);
                    sqlResults.add(sb.toString());
                }
            }
            // 特殊处理Hologres的insert overwrite wrapper函数
            // CALL hg_insert_overwrite_wrapper('table_name', 'partition_value', 'SELECT ...')
            if (AssetLineageConstant.HG_INSERT_OVERWRITE_WRAPPER.equalsIgnoreCase(callStatement.getProcedureName().toString())) {
                if (callStatement.getParameters() != null && callStatement.getParameters().size() >= 3) {
                    // 第0个参数：目标表名
                    String targetTableName = null;
                    if (callStatement.getParameters().get(0).toString().contains("::")) {
                        targetTableName = removeQuotation(callStatement.getParameters().get(0).toString().split("::")[0]);
                    } else {
                        targetTableName = removeQuotation(callStatement.getParameters().get(0).toString());
                    }
                    // 最后一个参数：SELECT 语句（可能是索引2或索引3）
                    String selectExpr;
                    int selectParamIndex = callStatement.getParameters().size() - 1;
                    selectExpr = removeQuotation(callStatement.getParameters().get(selectParamIndex).toString());
                    StringBuilder sb = new StringBuilder();
                    // 转换为 insert overwrite into
                    sb.append("insert  into ").append(targetTableName).append(" ").append(selectExpr);
                    sqlResults.add(sb.toString());
                }
            }
            // 特殊处理Hologres的insert overwrite函数
            // CALL hg_insert_overwrite('table_name', 'partition_value', 'SELECT ...')
            if (AssetLineageConstant.HG_INSERT_OVERWRITE.equalsIgnoreCase(callStatement.getProcedureName().toString())) {
                if (callStatement.getParameters() != null && callStatement.getParameters().size() >= 2) {
                    // 第0个参数：目标表名
                    String targetTableName = removeQuotation(callStatement.getParameters().get(0).toString());
                    // 最后一个参数：SELECT 语句
                    String selectExpr = removeQuotation(callStatement.getParameters().get(1).toString());
                    if (!selectExpr.toLowerCase().contains("select ")) {
                        int selectParamIndex = callStatement.getParameters().size() - 1;
                        selectExpr = removeQuotation(callStatement.getParameters().get(selectParamIndex).toString());
                    }
                    StringBuilder sb = new StringBuilder();
                    // 转换为 insert
                    sb.append("insert into ").append(targetTableName).append(" ").append(selectExpr);
                    sqlResults.add(sb.toString());
                }
            }
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement alterStatement = (SQLAlterTableStatement) sqlStatement;
            List<String> newSqlResults = new ArrayList<>();
            for (SQLAlterTableItem item : alterStatement.getItems()) {
                if (item instanceof SQLAlterTableRename) {
                    SQLAlterTableRename alterTableRename = (SQLAlterTableRename) item;
                    String sourceTableName = removeQuotation(alterStatement.getTableSource().toString());
                    String targetTableName = removeQuotation(alterTableRename.getToName().toString());
                    String sourceTableName1 = null;
                    if (sourceTableName.contains(".")) {
                        sourceTableName1 = sourceTableName.split("\\.")[1];
                    }
                    // 遍历所有 SQL，替换匹配的表名
                    for (String sqlResult : sqlResults) {
                        // 如果 SQL 中包含源表名，则替换为目标表名
                        if (sqlResult.contains(sourceTableName)) {
                            newSqlResults.add(sqlResult.replace(sourceTableName, targetTableName));
                        } else if (sourceTableName1 != null && sqlResult.contains(sourceTableName1)) {
                            newSqlResults.add(SqlConverter.replaceSql(sqlResult, sourceTableName1, targetTableName));
                        } else {
                            // 不包含源表名的 SQL，保持不变
                            newSqlResults.add(sqlResult);
                        }
                    }
                }
            }
            // 清空并更新
            sqlResults.clear();
            sqlResults.addAll(newSqlResults);
        } else if (sqlStatement instanceof SQLSelectStatement) {
            // 对于单纯的 SELECT 语句，如果需要对其进行血缘分析，直接加入
            //sqlResults.add(SQLUtils.toSQLString(sqlStatement, databaseType));
        } else {
            // 其他类型的语句，默认直接加入，或者根据需要进行过滤
            sqlResults.add(SQLUtils.toSQLString(sqlStatement, databaseType));
        }

    }

    /**
     * 获取alter table 语句的sourceTableName和TargetTableName.
     *
     * @param sql 待处理字符串
     * @param  databaseType 数据库类型
     * @return alter 表的映射关系
     */
    public static List<Map<String, String>> getAlterTableSourceToTargetTableName(String sql, String databaseType) {
        sql = SqlVariableResolver.replaceSqlVariables(sql);
        sql = SqlConverter.convertSqlAttachParttion(sql);
        sql = preprocessUnsupportedStatements(sql, getDbType(databaseType));
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, databaseType);
        List<Map<String, String>> tableResults = new ArrayList<>();
        for (SQLStatement sqlStatement : sqlStatements) {
            if (sqlStatement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement alterStatement = (SQLAlterTableStatement) sqlStatement;
                for (SQLAlterTableItem item : alterStatement.getItems()) {
                    if (item instanceof SQLAlterTableRename) {
                        SQLAlterTableRename alterTableRename = (SQLAlterTableRename) item;
                        String sourceTableName = removeQuotation(alterStatement.getTableSource().toString());
                        String targetTableName = removeQuotation(alterTableRename.getToName().toString());
                        Map<String, String> tableMap = new HashMap<>();
                        tableMap.put(sourceTableName, targetTableName);
                        tableResults.add(tableMap);
                    }
                }
            }
        }
        return tableResults;
    }

    /**
     * 移除字符串两端的单引号.
     *
     * @param str 待处理字符串
     * @return 移除引号后的字符串
     */
    private static String removeQuotation(String str) {
        if (StringUtils.isEmpty(str)) {
            return str;
        }
        if (str.startsWith("'") && str.endsWith("'") && str.length() > 1) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    /**
     * 返回合适Druid解析的类型.
     *
     * @param databaseType 数据库类型
     * @return 返回Druid对应的DbType
     */
    public static DbType getDbType(String databaseType) {
        DatabaseTypeEnum databaseTypeEnum = DatabaseTypeEnum.fromString(databaseType);

        if (databaseTypeEnum == null) {
            return null;
        }

        switch (databaseTypeEnum) {
            case MAXCOMPUTE : {
                return JdbcConstants.ODPS;
            }
            case HOLOGRES : {
                return JdbcConstants.POSTGRESQL;
            }
            default : {
                return null;
            }
        }
    }

    /**
     * 返回合适Druid解析的类型.
     *
     * @param sql          sql语句
     * @param databaseType 数据库类型
     * @return 返回Druid对应的DbType
     */
    public static Set<String> getTables(String sql, String databaseType) {
        DbType dbType = getDbType(databaseType);
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, dbType);
        Set<String> tableNames = new HashSet<>();
        SchemaStatVisitor visitor = null;
        if (dbType == JdbcConstants.ODPS) {
            visitor = new HiveSchemaStatVisitor();
        } else if (dbType == JdbcConstants.POSTGRESQL) {
            visitor = new PGSchemaStatVisitor();
        }

        if (visitor == null) {
            return tableNames;
        }
        sqlStatements.get(0).accept(visitor);
        Map<TableStat.Name, TableStat> tables2 = visitor.getTables();

        for (TableStat.Name name : tables2.keySet()) {
            tableNames.add(name.getName().toLowerCase());
        }
        return tableNames;
    }

    /**
     * 转换Hive/Spark风格的WITH语法为内联子查询.
     * 支持：
     * 1. INSERT INTO table_name WITH cte AS (...) SELECT ... FROM cte ...
     *    -> INSERT INTO table_name SELECT ... FROM (cte定义) AS cte ...
     * 2. CREATE TABLE table_name AS WITH cte AS (...) SELECT ... FROM cte ...
     *    -> CREATE TABLE table_name AS SELECT ... FROM (cte定义) AS cte ...
     *
     * @param sql 原始SQL
     * @return 转换后的SQL，如果不需要转换则返回原SQL
     */
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private static String convertInsertWithCte(String sql) {
        if (StringUtils.isEmpty(sql)) {
            return sql;
        }

        String trimmedSql = sql.trim();

        try {
            // 匹配 CREATE TABLE table_name AS WITH ... 或 INSERT INTO table_name WITH ...
            // 忽略大小写，使用[\s\r\n]替代\\s以匹配换行符
            if (!trimmedSql
                    .matches("(?i)^[\\s\\r\\n]*(create[\\s\\r\\n]+table|insert[\\s\\r\\n]+into)[\\s\\r\\n]+\\S+[\\s\\r\\n]+with[\\s\\r\\n]+.*")) {
                return sql;
            }

            // 1. 提取 CREATE TABLE table_name AS 或 INSERT INTO table_name 部分
            // 使用[\s\r\n]替代\\s以匹配换行符
            Pattern targetPattern = Pattern.compile(
                    "(?i)^([\\s\\r\\n]*create[\\s\\r\\n]+table[\\s\\r\\n]+[\\w.]+[\\s\\r\\n]+as|[\\s\\r\\n]*insert[\\s\\r\\n]+into[\\s\\r\\n]+[\\w.]+)[\\s\\r\\n]+",
                    Pattern.CASE_INSENSITIVE);
            Matcher targetMatcher = targetPattern.matcher(trimmedSql);
            if (!targetMatcher.find()) {
                return sql;
            }
            String targetClause = targetMatcher.group(1);
            int targetEndPos = targetMatcher.end();
            // 2. 从 WITH 开始，找到 WITH 子句的结束位置
            // 需要正确处理嵌套括号
            int withStartPos = targetEndPos;
            String remainingSql = trimmedSql.substring(withStartPos);

            // 检查是否以 WITH 开头，使用[\s\r\n]匹配换行符
            if (!remainingSql.matches("(?i)^[\\s\\r\\n]*with[\\s\\r\\n]+.*")) {
                return sql;
            }

            // 找到 WITH 子句的结束位置
            // WITH 子句格式：WITH cte1 AS (...), cte2 AS (...) SELECT ...
            int cteEndPos = findCteEndPos(remainingSql);

            if (cteEndPos <= 0) {
                return sql;
            }

            String cteClause = remainingSql.substring(0, cteEndPos).trim();
            String mainQuery = remainingSql.substring(cteEndPos).trim();

            // 3. 提取 CTE 名称和定义
            // 格式：cte_name AS (...)
            Pattern ctePattern = Pattern.compile("(?i)^(\\w+)[\\s\\r\\n]+as[\\s\\r\\n]+\\((.*)\\)[\\s\\r\\n]*$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher cteMatcher = ctePattern.matcher(cteClause);
            if (!cteMatcher.find()) {
                return sql;
            }
            String cteName = cteMatcher.group(1);
            String cteDefinition = cteMatcher.group(2);

            // 4. 在主查询中替换 CTE 引用为子查询
            // 将 FROM cte_name 替换为 FROM (cte定义) AS cte_name
            String inlinedSql = inlineCteInQuery(mainQuery, cteName, cteDefinition);

            // 5. 组装最终SQL
            String result = targetClause + " " + inlinedSql;

            log.info("转换前SQL: {}", sql);
            log.info("转换后SQL: {}", result);

            return result;
        } catch (Exception e) {
            log.warn("Druid转换INSERT WITH失败: {}", e.getMessage());
        }

        return sql;
    }

    /**
     * 在查询语句中内联CTE引用.
     * 将 FROM cte_name 替换为 FROM (cte定义) AS cte_name
     * 将 JOIN cte_name 替换为 JOIN (cte定义) AS cte_name
     *
     * @param query 主查询SQL
     * @param cteName CTE名称
     * @param cteDefinition CTE定义
     * @return 内联后的SQL
     */
    private static String inlineCteInQuery(String query, String cteName, String cteDefinition) {
        // 替换 FROM cte_name 或 FROM alias AS cte_name 为子查询
        // 匹配模式：FROM/JOIN cte_name [AS alias] 或 FROM/JOIN alias AS cte_name
        // 使用[\s\r\n]替代\\s以匹配换行符
        String pattern = "(?i)(\\b(from|join)[\\s\\r\\n]+)" + cteName + "([\\s\\r\\n]+|$)";

        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(query);

        if (m.find()) {
            String keyword = m.group(1); // FROM 或 JOIN
            String replacement = keyword + "(" + cteDefinition + ") AS " + cteName;

            return m.replaceFirst(replacement);
        }

        return query;
    }

    /**
     * 找到CTE子句的结束位置.
     * WITH 子句可能包含多个 CTE，每个 CTE 用逗号分隔
     * 需要正确处理嵌套括号
     *
     * @param sql 从 WITH 开始的 SQL 字符串
     * @return CTE 子句的结束位置（相对于输入字符串）
     */
    private static int findCteEndPos(String sql) {
        int pos = 0;
        int parenDepth = 0;
        boolean inCte = true;

        // 跳过 WITH 关键字
        while (pos < sql.length()) {
            char c = sql.charAt(pos);
            if (!Character.isWhitespace(c)) {
                if (sql.substring(pos).matches("(?i)^with\\b.*")) {
                    pos += 4; // 跳过 "WITH"
                    while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
                        pos++;
                    }
                    break;
                }
            }
            pos++;
        }

        // 解析 CTE 定义
        while (pos < sql.length() && inCte) {
            char c = sql.charAt(pos);

            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
                // 当括号深度回到0，可能是一个 CTE 结束
                if (parenDepth == 0) {
                    // 检查后面是否是逗号（还有下一个 CTE）或其他
                    String remaining = sql.substring(pos + 1).trim();
                    if (remaining.startsWith(",")) {
                        // 还有下一个 CTE，跳过逗号继续
                        pos += 1;
                        while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
                            pos++;
                        }
                    } else {
                        // CTE 结束，后面应该是主查询（SELECT/INSERT/UPDATE/DELETE 等）
                        inCte = false;
                    }
                }
            }
            pos++;
        }

        // 跳过空白字符，返回主查询开始位置
        while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
            pos++;
        }

        return pos;
    }

    /**
     * 完善方案：展开 CTE 子句，将 WITH ... SELECT 转换为内联子查询.
     * 方案说明：
     * 1. 收集所有 CTE 定义
     * 2. 按顺序展开 CTE（后面的 CTE 可以引用前面的 CTE）
     * 3. 确保括号匹配正确
     * 4. 替换主查询中的 CTE 别名为子查询
     *
     * @param select SQLSelect 对象，包含 WITH 子句
     * @param targetTableName 目标表名
     * @return 展开后的 SQL
     */
    private static String expandCteInCreateTable(SQLSelect select, String targetTableName) {
        try {
            // 1. 获取主查询（去掉 WITH 子句）
            String mainQueryStr = SQLUtils.toSQLString(select.getQuery(), JdbcConstants.POSTGRESQL);
            log.debug("原始主查询: {}", mainQueryStr);

            // 2. 收集所有 CTE 定义
            List<SQLWithSubqueryClause.Entry> entries = select.getWithSubQuery().getEntries();
            int cteCount = entries.size();
            log.debug("CTE 数量: {}", cteCount);

            // 3. 按顺序展开 CTE
            // 存储展开后的 CTE 定义
            Map<String, String> expandedCteMap = new LinkedHashMap<>();
            // 存储原始 CTE 定义
            Map<String, String> originalCteMap = new LinkedHashMap<>();

            for (SQLWithSubqueryClause.Entry entry : entries) {
                String alias = entry.getAlias();
                String subQueryStr = SQLUtils.toSQLString(entry.getSubQuery(), JdbcConstants.POSTGRESQL);
                originalCteMap.put(alias, subQueryStr);
                log.debug("原始 CTE [{}]: {}", alias, subQueryStr);
            }

            // 按顺序展开 CTE（从最后一个开始，确保引用的是已经展开的版本）
            // 但这里我们需要从前往后展开，因为前面的 CTE 会被后面的 CTE 引用
            for (int i = 0; i < cteCount; i++) {
                SQLWithSubqueryClause.Entry entry = entries.get(i);
                String alias = entry.getAlias();
                String subQueryStr = originalCteMap.get(alias);

                // 替换当前 CTE 中引用的其他 CTE
                for (int j = 0; j < i; j++) {
                    String refAlias = entries.get(j).getAlias();
                    if (subQueryStr.toLowerCase().contains(refAlias.toLowerCase())) {
                        // 使用单词边界匹配
                        String pattern = "(?i)\\b" + Pattern.quote(refAlias) + "\\b";
                        if (Pattern.compile(pattern).matcher(subQueryStr).find()) {
                            String replacement = "(" + expandedCteMap.get(refAlias) + ")";
                            log.debug("CTE [{}] 展开引用 [{}]: {} -> {}", alias, refAlias, refAlias, replacement);
                            subQueryStr = subQueryStr.replaceAll(pattern, replacement);
                        }
                    }
                }

                // 去除子查询末尾可能多余的括号（如果子查询以 ) 结尾，且不是有效的 SQL 结束）
                subQueryStr = normalizeSubqueryParens(subQueryStr);

                expandedCteMap.put(alias, subQueryStr);
                log.debug("展开后 CTE [{}]: {}", alias, subQueryStr);
            }

            // 4. 替换主查询中的 CTE 别名
            String finalQuery = mainQueryStr;
            for (int i = cteCount - 1; i >= 0; i--) {
                String alias = entries.get(i).getAlias();
                String expandedSubQuery = expandedCteMap.get(alias);

                // 使用单词边界匹配，确保只替换完整的别名
                String pattern = "(?i)\\b" + Pattern.quote(alias) + "\\b";
                if (Pattern.compile(pattern).matcher(finalQuery).find()) {
                    log.debug("替换主查询中的 CTE [{}]: {} -> (...)", alias, alias);
                    finalQuery = finalQuery.replaceAll(pattern, Matcher.quoteReplacement("(" + expandedSubQuery + ")"));
                }
            }

            log.debug("最终展开的主查询: {}", finalQuery);

            return "insert into " + targetTableName + " " + finalQuery;

        } catch (Exception e) {
            log.warn("CTE 展开失败，返回原始 SQL: {}", e.getMessage());
            // 展开失败时，返回简单的转换结果
            return SQLUtils.toSQLString(select, JdbcConstants.POSTGRESQL)
                    .replaceFirst("(?i)^INSERT\\s+INTO\\s+\\S+\\s+WITH\\s+.*?\\)\\s*", "insert into " + targetTableName + " ");
        }
    }

    /**
     * 规范化子查询的括号.
     * Druid 生成的子查询可能以 ) 结尾，需要正确处理.
     *
     * @param subQuery 子查询字符串
     * @return 规范化后的子查询
     */
    private static String normalizeSubqueryParens(String subQuery) {
        if (subQuery == null || subQuery.length() < 2) {
            return subQuery;
        }

        String trimmed = subQuery.trim();

        // 如果子查询以 ) 结尾，检查是否需要去除多余的括号
        // 例如：(SELECT ... FROM t) 变成 SELECT ... FROM t
        if (trimmed.endsWith(")")) {
            // 计算括号平衡
            int depth = 0;
            boolean inString = false;
            char stringChar = 0;
            int len = trimmed.length();

            for (int i = 0; i < len; i++) {
                char c = trimmed.charAt(i);

                // 处理字符串
                if (c == '\'' || c == '"') {
                    if (!inString) {
                        inString = true;
                        stringChar = c;
                    } else if (c == stringChar) {
                        inString = false;
                    }
                    continue;
                }

                if (inString) {
                    continue;
                }

                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
            }

            // 如果整个子查询被一对额外的括号包裹，去除外层括号
            // 条件：深度为 0 时，且开头有对应的 (
            if (depth == 0 && trimmed.startsWith("(") && trimmed.endsWith(")")) {
                // 检查外层括号是否包裹了整个内容
                int innerDepth = 0;
                boolean found = false;
                for (int i = 0; i < len - 1; i++) {
                    char c = trimmed.charAt(i);
                    if (c == '(') {
                        innerDepth++;
                    } else if (c == ')') {
                        innerDepth--;
                        if (innerDepth == 0) {
                            // 找到最后一个闭合括号
                            if (i == len - 2) {
                                // 外层括号正好包裹整个内容
                                found = true;
                            }
                            break;
                        }
                    }
                }
                if (found) {
                    // 去除最外层括号
                    String inner = trimmed.substring(1, len - 1).trim();
                    // 确保 inner 以 SELECT 或其他有效的子查询开头
                    if (inner.toUpperCase().startsWith("SELECT")
                            || inner.toUpperCase().startsWith("WITH")
                            || inner.toUpperCase().startsWith("(")) {
                        return inner;
                    }
                }
            }
        }

        return trimmed;
    }

}

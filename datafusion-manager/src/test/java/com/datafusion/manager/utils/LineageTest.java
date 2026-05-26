package com.datafusion.manager.utils;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLLateralViewTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGInsertStatement;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/15
 * @since 2025/10/15
 */
public class LineageTest {
    
    public static class PgVisitor extends PGASTVisitorAdapter {
        
        @Override
        public void endVisit(SQLSelectStatement x) {
            System.out.println("----SQLSelectStatement----");
        }
        
        @Override
        public void endVisit(SQLInsertStatement x) {
            System.out.println("----SQLInsertStatement----");
        }
        
        @Override
        public void endVisit(PGInsertStatement x) {
            System.out.println("----PGInsertStatement----");
        }
        
        @Override
        public void endVisit(SQLUnionQuery x) {
            System.out.println("----SQLUnionQuery----");
        }
        
        @Override
        public void endVisit(SQLSelectQueryBlock x) {
            System.out.println("----SQLSelectQueryBlock----");
        }
        
        @Override
        public boolean visit(SQLSelectStatement x) {
            System.out.println("----SQLSelectStatement----");
            if (x.getParent() instanceof SQLInsertStatement) {
                // 不 pushContext，直接返回 true 让其查询部分被访问
                return true;
            }
            if (x.getParent() == null // 最外层 SELECT
                    // 移除此处对 SQLInsertStatement 的判断，因为上面已经处理
                    || x.getParent() instanceof SQLWithSubqueryClause.Entry // CTE
                    || x.getParent() instanceof SQLSubqueryTableSource // 子查询
                    || x.getParent() instanceof SQLUnionQuery) { // UNION 的左右子查询，但其上下文由 UNION 统一管理
                return true;
            }
            return true;
        }
        
        @Override
        public boolean visit(SQLSelectQueryBlock x) {
            System.out.println("----SQLSelectQueryBlock----");
            // 这里可以简单地返回 true，让其子节点被访问
            return true;
        }
        
        @Override
        public boolean visit(SQLLateralViewTableSource x) {
            System.out.println("----SQLLateralViewTableSource----");
            // 访问 LATERAL VIEW 的基础表源
            if (x.getTableSource() != null) {
                x.getTableSource().accept(this);
            }
            return false; // 已处理，不向下访问其子节点
        }
        
        /**
         * 处理表源 (FROM 中的表或子查询).
         */
        @Override
        public boolean visit(SQLExprTableSource x) {
            System.out.println("----SQLExprTableSource----");
            return false; // 不再向下访问表名表达式
        }
        
        @Override
        public boolean visit(SQLJoinTableSource x) {
            // Join 语句内部的表源会被单独 visit，无需在此处重复处理 aliasMaps
            // 访问 JOIN 条件中的列，这些列作为来源但没有直接的目标
            System.out.println("----SQLJoinTableSource----");
            return true;
        }
        
        @Override
        public boolean visit(SQLSubqueryTableSource x) {
            System.out.println("----SQLSubqueryTableSource----");
            return false;
        }
        
        @Override
        public boolean visit(PGInsertStatement x) {
            System.out.println("----PGInsertStatement----");
            if (x.getQuery() != null) {
                x.getQuery().accept(this);
            }
            return false; // 已处理，不向下访问其子节点
        }
        
        @Override
        public boolean visit(SQLInsertStatement x) {
            System.out.println("----PGInsertStatement----");
            if (x.getQuery() != null) {
                x.getQuery().accept(this);
            }
            return false; // 已处理，不向下访问其子节点
        }
        
        @Override
        public boolean visit(SQLUpdateStatement x) {
            //todo 与血缘无关
            return false;
        }
        
        @Override
        public boolean visit(SQLDeleteStatement x) {
            //todo 与血缘无关
            return false;
        }
        
        /**
         * 处理 UNION 查询.
         */
        @Override
        public boolean visit(SQLUnionQuery x) {
            x.getLeft().accept(this);
            x.getRight().accept(this);
            return true;
        }
        
        /**
         * 处理 CTE (WITH 子句).
         */
        @Override
        public boolean visit(SQLWithSubqueryClause x) {
            System.out.println("----SQLWithSubqueryClause----");
            for (SQLWithSubqueryClause.Entry entry : x.getEntries()) {
                entry.getSubQuery().accept(this); // 递归处理 CTE 的查询
                if (entry.getSubQuery().getQuery() instanceof SQLSelectQueryBlock) {
                    SQLSelectQueryBlock queryBlock = (SQLSelectQueryBlock) entry.getSubQuery().getQuery();
                    for (SQLSelectItem item : queryBlock.getSelectList()) {
                        String columnName;
                        if (item.getAlias() != null) {
                            columnName = item.getAlias().toLowerCase();
                        } else if (item.getExpr() instanceof SQLIdentifierExpr) {
                            columnName = ((SQLIdentifierExpr) item.getExpr()).getName().toLowerCase();
                        } else if (item.getExpr() instanceof SQLPropertyExpr) {
                            columnName = ((SQLPropertyExpr) item.getExpr()).getName().toLowerCase();
                        } else if (item.getExpr() instanceof SQLAllColumnExpr) {
                            columnName = "*";
                        } else {
                            columnName = "unknown_cte_col_" + item.hashCode();
                        }
                    }
                }
            }
            return true;
        }
        
        /**
         * 处理 SELECT ITEM.
         */
        @Override
        public boolean visit(SQLSelectItem x) {
            System.out.println("----SQLSelectItem----");
            return false;
        }
    }
    
    @Test
    public void testSimpleInsertSelect() throws Exception {
        String sql = "insert into dw.table_a select * from (select * from dw.table_b) b";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.POSTGRESQL);
        for (SQLStatement stmt : sqlStatements) {
            PgVisitor visitor = new PgVisitor();
            stmt.accept(visitor);
            
        }
    
    }
    
    @Test
    public void test() throws Exception {
        SqlParser.Config parserConfig = SqlParser.config()
                .withParserFactory(SqlBabelParserImpl.FACTORY)
                .withConformance(SqlConformanceEnum.BABEL)
                .withLex(Lex.BIG_QUERY)
                .withUnquotedCasing(Casing.UNCHANGED)
                .withCaseSensitive(false);
        SchemaPlus rootSchema = CalciteSchema.createRootSchema(true).plus();
        rootSchema.add("tmp_query_sebu1_dwd_node_month_time_sharing_incr_agg_02_02_8l",
                new AbstractTable() {
                    @Override
                    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
                        return typeFactory.builder()
                                .add("30_min", SqlTypeName.VARCHAR)
                                .build();
                    }
                });
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(rootSchema)
                .parserConfig(parserConfig)
                .build();
        
        //进行血缘解析
        Planner planner = Frameworks.getPlanner(config);
        ScalarFunction sqlFunction = ScalarFunctionImpl.create(
                HologresScalarFunction.class, "IF" // 对应HologresScalarFunction.CONCAT_WS方法
        );
/*        SqlBasicFunction myDbFunc = new SqlBasicFunction(
                "date_format", // 函数名
                SqlKind.OTHER_FUNCTION, // SQL Kind，这里表示这是一个普通的函数
                ReturnTypes.explicit(SqlTypeName.VARCHAR), // 返回类型：VARCHAR
                // 参数类型检查：第一个参数是字符串，第二个参数是数值
                OperandTypes.family(SqlTypeFamily.DATE, SqlTypeFamily.STRING),
                // 返回类型推断：如果没有明确指定，可以设置为null，Calcite会使用ReturnTypes
                SqlTypeFamily.STRING,
                SqlFunctionCategory.STRING // 函数类别，这里是字符串函数
        );*/
        
        rootSchema.add("if", sqlFunction);
        SqlNode parse = planner.parse(
                "select IF(7 > 8,'test',t1.`30_min`) from tmp_query_sebu1_dwd_node_month_time_sharing_incr_agg_02_02_8l t1");
        SqlNode validate = planner.validate(parse);
        System.out.println("");
    }
    
    @Test
    public void testRex() throws SqlParseException {
        Pattern INSERT_OVERWRITE_WITH_PARTITION_PATTERN =
                Pattern.compile(
                        "(?i)INSERT\\s+OVERWRITE\\s+TABLE\\s+([a-zA-Z0-9_.]+)\\s+PARTITION\\s*\\(\\s*([a-zA-Z0-9_,\\s]+)\\s*\\)",
                        Pattern.CASE_INSENSITIVE
                );
        Matcher matcher = INSERT_OVERWRITE_WITH_PARTITION_PATTERN.matcher(
                "insert overwrite table sebu_dwb_device_incr_integration partition(distz, partition_date, mode, frequency) select * from source_table");
        if (matcher.find()) {
            System.out.println("000000000000000000");
        }
    }
    
    
    @Test
    public void testRex1() throws SqlParseException {
        String originSql =
                "insert into tmp_query_sebu1_dwd_vpp_device_15min_incr_04_8l SELECT t1.sn, t1.15min_action_time, t1.action_date, t1.tag\n" +
                        "\t, avg(t1.tag_value) AS avg_p_value\n" +
                        "\t, concat_ws(',', collect_set(CAST(t1.tag_value AS STRING))) AS tag_value_list\n" +
                        "\t, avg(t1.theory_p) AS theory_p, t1.distributed_code, t1.distz\n" +
                        "FROM (\n" +
                        "\tSELECT t1.sn, t1.15min_action_time, t1.action_date, t1.tag, t1.tag_value\n" +
                        "\t\t, CASE \n" +
                        "\t\t\tWHEN t1.node_type_id = 'grid'\n" +
                        "\t\t\t\tAND t1.tag_value >= 0\n" +
                        "\t\t\tTHEN t1.theory_down_p\n" +
                        "\t\t\tELSE t1.theory_p\n" +
                        "\t\tEND AS theory_p, t1.distributed_code, t1.distz\n" +
                        "\tFROM tmp_query_sebu1_dwd_vpp_device_15min_incr_02_8l t1\n" +
                        "\tWHERE t1.tag = 'p'\n" +
                        "\t\tAND t1.action_date >= '${base_date}'\n" +
                        "\t\tAND CASE \n" +
                        "\t\t\tWHEN t1.node_type_id = 'grid' THEN \n" +
                        "\t\t\t\tCASE \n" +
                        "\t\t\t\t\tWHEN t1.tag_value >= 0 THEN abs(t1.tag_value) < t1.theory_down_p\n" +
                        "\t\t\t\t\t\tOR t1.theory_down_p IS NULL\n" +
                        "\t\t\t\t\tELSE abs(t1.tag_value) > t1.theory_p\n" +
                        "\t\t\t\t\tOR t1.theory_p IS NULL\n" +
                        "\t\t\t\tEND\n" +
                        "\t\t\tELSE abs(t1.tag_value) < t1.theory_p\n" +
                        "\t\t\tOR t1.theory_p IS NULL\n" +
                        "\t\tEND\n" +
                        ") t1\n" +
                        "GROUP BY t1.sn, \n" +
                        "\tt1.action_date, \n" +
                        "\tt1.15min_action_time, \n" +
                        "\tt1.tag, \n" +
                        "\tt1.distributed_code, \n" +
                        "\tt1.distz";
        
        
    }
    
    public static String fixInvalidIdentifiers(String sql) {
        // 匹配模式： (\\b\\w+) 表示捕获一个单词边界后的一个或多个字母数字下划线作为别名 (如 t1)
        // \\. 表示匹配点号
        // ([0-9][a-zA-Z0-9_]*) 表示捕获一个以数字开头，后面跟着字母数字下划线的标识符 (如 15min_action_time)
        // 这个模式是针对 'alias.numeric_start_identifier' 这种情况
        // 如果标识符独立出现，例如 'SELECT 15min_action_time FROM ...'，则需要调整正则表达式
        // 但根据你的SQL，都是 't1.15min_action_time' 这种形式，所以此模式应该适用。
        Pattern pattern = Pattern.compile("(\\b\\w+)\\.([0-9][a-zA-Z0-9_]*)");
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String alias = matcher.group(1); // 例如 "t1"
            String invalidIdentifier = matcher.group(2); // 例如 "15min_action_time"
            // 将其替换为 alias.`invalidIdentifier`
            matcher.appendReplacement(sb, alias + ".`" + invalidIdentifier + "`");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    
}

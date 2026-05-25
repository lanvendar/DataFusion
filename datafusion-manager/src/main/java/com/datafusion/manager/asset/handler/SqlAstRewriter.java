package com.datafusion.manager.asset.handler;

import org.apache.calcite.sql.SqlBasicTypeNameSpec;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlShuttle;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL AST 转换工具类.
 * 强制规范：所有 if/for 必须使用大括号.
 * 修复点：死循环根治、类型推导错误(BIGINT not found)、版本兼容性.
 * @author zyw
 * @version 1.0.0 , 2025/10/15
 * @since 2025/10/15
 */
public class SqlAstRewriter {

    /**
     * 1.
     */
    private static final String OP_CONCAT = "CONCAT";

    /**
     * 1.
     */
    private static final String OP_CAST = "CAST";

    /**
     * 1.
     */
    private static final String OP_IF = "IF";

    /**
     * 1.
     */
    private static final String OP_NVL = "NVL";

    /**
     * 1.
     */
    private static final String OP_TO_CHAR = "TO_CHAR";

    /**
     * 1.
     */
    private static final String OP_NULL = "NULL";

    /**
     * 1.
     */
    private static final String TYPE_DATE = "DATE";

    /**
     * 1.
     */
    private static final String TYPE_DATETIME = "DATETIME";

    /**
     * 1.
     */
    private static final String TYPE_TIMESTAMP = "TIMESTAMP";

    /**
     * 1.
     */
    private static final String TYPE_VARCHAR = "VARCHAR";

    /**
     * 1.
     */
    private static final String TYPE_DECIMAL = "DECIMAL";

    /**
     * 1.
     */
    private static final String TYPE_BIGINT = "BIGINT";

    // =========================================================================
    // 入口方法
    // =========================================================================

    /**
     * rewriteConcat.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteConcat(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new ConcatRewriter());
    }

    /**
     * forceCastToTimestamp.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode forceCastToTimestamp(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new ForceCastTimestampRewriter());
    }

    /**
     * forceCastToVarchar.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode forceCastToVarchar(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new ForceCastVarcharRewriter());
    }

    /**
     * rewriteIf.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteIf(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new IfToCaseRewriter());
    }

    /**
     * rewriteNvlToCoalesce.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteNvlToCoalesce(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new NvlToCoalesceRewriter());
    }

    /**
     * rewriteToCharToCast.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteToCharToCast(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new ToCharToCastRewriter());
    }

    /**
     * fixNullTypeInCaseWhen.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode fixNullTypeInCaseWhen(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new NullTypeInCaseWhenRewriter());
    }

    /**
     * rewriteJsonAggToJsonArray.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteJsonAggToJsonArray(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new JsonAggRewriter());
    }

    /**
     * rewriteDoubleColonToCast.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteDoubleColonToCast(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new DoubleColonRewriter());
    }

    /**
     * rewriteMinusToBigint.
     * @param sqlNode sqlNode
     * @return SqlNode
     */
    public static SqlNode rewriteMinusToBigint(SqlNode sqlNode) {
        if (sqlNode == null) {
            return null;
        }
        return sqlNode.accept(new MinusRewriter());
    }

    // =========================================================================
    // 逻辑实现类 (Rewriters)
    // =========================================================================

    /** 1. ConcatRewriter: 多参转嵌套 */
    private static class ConcatRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_CONCAT.equalsIgnoreCase(c.getOperator().getName()) && c.operandCount() > 2) {
                return buildNestedConcat(c.getOperandList(), c.getParserPosition());
            }
            return c;
        }

        private SqlNode buildNestedConcat(List<SqlNode> args, SqlParserPos pos) {
            if (args.isEmpty()) {
                return null;
            }
            if (args.size() == 1) {
                return args.get(0);
            }
            SqlNode left = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                left = SqlStdOperatorTable.CONCAT.createCall(pos, left, args.get(i));
            }
            return left;
        }
    }

    /** 2. ForceCastTimestampRewriter */
    private static class ForceCastTimestampRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_CAST.equalsIgnoreCase(c.getOperator().getName()) && c.operandCount() >= 2) {
                String typeName = getTypeNameFromNode(c.operand(1));
                if (TYPE_DATE.equalsIgnoreCase(typeName) || TYPE_DATETIME.equalsIgnoreCase(typeName)) {
                    return SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), c.operand(0),
                            makeTypeSpec(SqlTypeName.TIMESTAMP, c.getParserPosition()));
                }
            }
            return c;
        }
    }

    /** 3. ForceCastVarcharRewriter */
    private static class ForceCastVarcharRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_CAST.equalsIgnoreCase(c.getOperator().getName()) && c.operandCount() >= 2) {
                return SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), c.operand(0),
                        makeTypeSpec(SqlTypeName.VARCHAR, c.getParserPosition()));
            }
            return c;
        }
    }

    /** 4. IfToCaseRewriter */
    private static class IfToCaseRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_IF.equalsIgnoreCase(c.getOperator().getName()) && c.operandCount() == 3) {
                SqlNodeList whenList = new SqlNodeList(c.getParserPosition());
                whenList.add(c.operand(0));
                SqlNodeList thenList = new SqlNodeList(c.getParserPosition());
                thenList.add(c.operand(1));
                return SqlStdOperatorTable.CASE.createCall(c.getParserPosition(), null, whenList, thenList, c.operand(2));
            }
            return c;
        }
    }

    /** 5. NvlToCoalesceRewriter */
    private static class NvlToCoalesceRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_NVL.equalsIgnoreCase(c.getOperator().getName())) {
                return SqlStdOperatorTable.COALESCE.createCall(c.getParserPosition(), c.getOperandList());
            }
            return c;
        }
    }

    /** 6. ToCharToCastRewriter */
    private static class ToCharToCastRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (OP_TO_CHAR.equalsIgnoreCase(c.getOperator().getName()) && c.operandCount() >= 1) {
                return SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), c.operand(0),
                        makeTypeSpec(SqlTypeName.VARCHAR, c.getParserPosition()));
            }
            return c;
        }
    }

    /** 7. NullTypeInCaseWhenRewriter */
    private static class NullTypeInCaseWhenRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode visited = super.visit(call);
            if (!(visited instanceof SqlCall)) {
                return visited;
            }
            SqlCall c = (SqlCall) visited;
            if (c.getKind() != SqlKind.CASE) {
                return c;
            }

            List<SqlNode> ops = c.getOperandList();
            SqlNode typeTemplate = findFirstNonNullResult(ops);
            if (typeTemplate == null) {
                return c;
            }

            boolean changed = false;
            List<SqlNode> newOps = new ArrayList<>(ops);
            SqlNode targetType = inferTypeFromTemplate(typeTemplate, c.getParserPosition());

            for (int i = 1; i < ops.size(); i += 2) {
                if (isNullLiteral(ops.get(i))) {
                    newOps.set(i, SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), ops.get(i), targetType));
                    changed = true;
                }
            }
            if (ops.size() % 2 == 1 && isNullLiteral(ops.get(ops.size() - 1))) {
                newOps.set(ops.size() - 1, SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), ops.get(ops.size() - 1), targetType));
                changed = true;
            }
            if (changed) {
                return c.getOperator().createCall(c.getParserPosition(), newOps);
            }
            return c;
        }

        private SqlNode findFirstNonNullResult(List<SqlNode> ops) {
            for (int i = 1; i < ops.size(); i += 2) {
                if (!isNullLiteral(ops.get(i))) {
                    return ops.get(i);
                }
            }
            if (ops.size() % 2 == 1) {
                SqlNode elseNode = ops.get(ops.size() - 1);
                if (!isNullLiteral(elseNode)) {
                    return elseNode;
                }
            }
            return null;
        }

        private SqlNode inferTypeFromTemplate(SqlNode template, SqlParserPos pos) {
            if (template instanceof SqlCall && ((SqlCall) template).getKind() == SqlKind.CAST) {
                return ((SqlCall) template).operand(1);
            }
            return makeTypeSpec(SqlTypeName.DECIMAL, pos);
        }
    }

    /** 8. JsonAggRewriter */
    private static class JsonAggRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            String name = c.getOperator().getName().toUpperCase();
            if ("JSON_AGG".equals(name)) {
                return SqlStdOperatorTable.JSON_ARRAY.createCall(c.getParserPosition(), c.getOperandList());
            }
            if ("JSON_BUILD_OBJECT".equals(name) || "JSON_OBJECT_AGG".equals(name)) {
                return SqlStdOperatorTable.JSON_OBJECT.createCall(c.getParserPosition(), c.getOperandList());
            }
            return c;
        }
    }

    /** 9. DoubleColonRewriter */
    private static class DoubleColonRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            String opName = c.getOperator().getName();
            if ("::".equals(opName) || "DOUBLE_COLON".equalsIgnoreCase(opName)) {
                SqlNode typeNode = c.operand(1);
                if (TYPE_DATE.equalsIgnoreCase(getTypeNameFromNode(typeNode))) {
                    typeNode = makeTypeSpec(SqlTypeName.TIMESTAMP, c.getParserPosition());
                }
                return SqlStdOperatorTable.CAST.createCall(c.getParserPosition(), c.operand(0), typeNode);
            }
            return c;
        }
    }

    /** 10. MinusRewriter */
    private static class MinusRewriter extends SqlShuttle {
        @Override
        public SqlNode visit(SqlCall call) {
            SqlNode processed = super.visit(call);
            if (!(processed instanceof SqlCall)) {
                return processed;
            }
            SqlCall c = (SqlCall) processed;
            if (c.getOperator().getKind() == SqlKind.MINUS && c.operandCount() == 2) {
                if (isAlreadyCasted(c.operand(0)) && isAlreadyCasted(c.operand(1))) {
                    return c;
                }
                boolean isTime = isTimeRef(c.operand(0)) || isTimeRef(c.operand(1));
                SqlNode typeSpec = makeTypeSpec(isTime ? SqlTypeName.TIMESTAMP : SqlTypeName.BIGINT, c.getParserPosition());
                return c.getOperator().createCall(c.getParserPosition(),
                        wrapInCast(c.operand(0), typeSpec, c.getParserPosition()),
                        wrapInCast(c.operand(1), typeSpec, c.getParserPosition()));
            }
            return c;
        }

        private boolean isAlreadyCasted(SqlNode n) {
            return n instanceof SqlCall && ((SqlCall) n).getKind() == SqlKind.CAST;
        }

        private SqlNode wrapInCast(SqlNode n, SqlNode type, SqlParserPos pos) {
            if (isAlreadyCasted(n)) {
                return n;
            }
            return SqlStdOperatorTable.CAST.createCall(pos, n, type);
        }

        private boolean isTimeRef(SqlNode n) {
            String s = n.toString().toLowerCase();
            return s.contains("time") || s.contains("date");
        }
    }

    // =========================================================================
    // 辅助工具
    // =========================================================================

    private static String getTypeNameFromNode(SqlNode node) {
        if (node instanceof SqlIdentifier) {
            return ((SqlIdentifier) node).getSimple();
        }
        if (node instanceof SqlDataTypeSpec) {
            return ((SqlDataTypeSpec) node).getTypeName().getSimple();
        }
        return "";
    }

    private static SqlDataTypeSpec makeTypeSpec(SqlTypeName typeName, SqlParserPos pos) {
        return new SqlDataTypeSpec(new SqlBasicTypeNameSpec(typeName, pos), pos);
    }

    private static boolean isNullLiteral(SqlNode node) {
        if (node instanceof SqlLiteral && ((SqlLiteral) node).getTypeName() == SqlTypeName.NULL) {
            return true;
        }
        if (node instanceof SqlCall && OP_NULL.equalsIgnoreCase(((SqlCall) node).getOperator().getName())) {
            return true;
        }
        return false;
    }
}

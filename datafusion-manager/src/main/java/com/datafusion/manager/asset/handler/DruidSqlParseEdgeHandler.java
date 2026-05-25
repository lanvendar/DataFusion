package com.datafusion.manager.asset.handler;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLOver;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLArrayExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLCaseExpr;
import com.alibaba.druid.sql.ast.expr.SQLCastExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLLiteralExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLLateralViewTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.ast.statement.SQLUnionQueryTableSource;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGInsertStatement;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGASTVisitorAdapter;
import com.datafusion.manager.asset.dto.DruidParseResultDto;
import com.datafusion.manager.asset.dto.EdgeTableColumnDto;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.EdgeColumnInfoDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Druid SQL 血缘解析处理器.
 * 核心机制：
 * 1. 作用域栈：处理嵌套子查询的别名隔离。
 * 2. 子查询物理化：在作用域销毁前将血缘打通至物理表，存入全局注册表。
 * 3. 位置映射：针对 INSERT INTO 无字段声明的情况，按 SELECT 顺序映射目标字段。
 * 4. 字段还原：支持输出带 Schema 的原始表名，适配外部过滤逻辑。
 * @author xufeng
 * @version 1.0.0, 2026/2/26
 * @since 2026/2/26
 */
@Data
@Slf4j
public class DruidSqlParseEdgeHandler {

    @Data
    public static class LineageVisitor extends PGASTVisitorAdapter {

        /**
         * metadataService.
         */
        private final MetadataService metadataService;

        /**
         * lineageEdges.
         */
        private final Set<EdgeTableColumnDto> lineageEdges = new HashSet<>();

        /**
         * 解析日志列表，记录 [SUCCESS] 和 [FAILURE] 信息.
         */
        private final List<String> parseLogs = new ArrayList<>();

        /**
         * 别名映射栈：存储当前层级可见的 别名 -> 物理表/子查询标记 的映射.
         */
        private final Stack<Map<String, String>> aliasMaps = new Stack<>();

        /**
         * 目标表名栈：记录当前 INSERT 或 CTAS 的目标表.
         */
        private final Stack<String> targetTableNames = new Stack<>();

        /**
         * 表达式依赖图栈：记录当前 SELECT 中 字段名 -> 依赖标识符列表（如 sum(a) 依赖 a）.
         */
        private final Stack<Map<String, List<String>>> selectItemSourcesMaps = new Stack<>();

        /**
         * 子查询全局注册表：Key 为子查询别名，Value 为该子查询输出列到物理表列的绝对路径映射.
         */
        private final Map<String, Map<String, List<String>>> subqueryRegistry = new HashMap<>();

        /**
         * 名称映射表：用于将解析用的纯表名（table）还原为外部传入的原始名（dw.table）.
         */
        private final Map<String, String> pureToOriginalNameMap;

        /**
         * LineageVisitor.
         * @param metadataService metadataService
         * @param nameMap nameMap
         */
        public LineageVisitor(MetadataService metadataService, Map<String, String> nameMap) {
            this.metadataService = metadataService;
            this.pureToOriginalNameMap = nameMap;
            aliasMaps.push(new HashMap<>());
            selectItemSourcesMaps.push(new HashMap<>());
            targetTableNames.push(null);
        }

        /**
         * 进入新作用域（如子查询）.
         * @param target target
         */
        private void pushContext(String target) {
            aliasMaps.push(new HashMap<>());
            selectItemSourcesMaps.push(new HashMap<>());
            targetTableNames.push(target);
        }

        /**
         * 退出当前作用域.
         */
        private void popContext() {
            if (aliasMaps.size() > 1) {
                aliasMaps.pop();
                selectItemSourcesMaps.pop();
                targetTableNames.pop();
            }
        }

        /**
         * 清理名称中的反引号、引号并转小写.
         * @param str str
         * @return String
         */
        private String clean(String str) {
            if (str == null) {
                return null;
            }
            return str.replace("`", "").replace("\"", "").trim().toLowerCase();
        }

        /**
         * 截取不带 schema 的纯表名（如 dw.test -> test）.
         * @param fullName fullName
         * @return String
         */
        private String getTableNameOnly(String fullName) {
            String cleaned = clean(fullName);
            if (cleaned != null && cleaned.contains(".")) {
                return cleaned.substring(cleaned.lastIndexOf(".") + 1);
            }
            return cleaned;
        }

        /**
         * 将纯表名还原为带 schema 的原始名称（以便外部过滤）.
         * @param internalName internalName
         * @return String
         */
        private String restoreOriginalName(String internalName) {
            return pureToOriginalNameMap.getOrDefault(internalName, internalName);
        }

        /**
         * 处理 PostgreSQL 风格的 INSERT.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(PGInsertStatement x) {
            String targetPure = getTableNameOnly(x.getTableSource().getExpr().toString());
            this.targetTableNames.pop();
            this.targetTableNames.push(targetPure);
            log.info("[Step 1] Target Table (PG): {}", targetPure);
            if (x.getQuery() != null) {
                x.getQuery().accept(this);
            }
            return false;
        }

        /**
         * 处理通用标准 INSERT.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLInsertStatement x) {
            String targetPure = getTableNameOnly(x.getTableSource().getExpr().toString());
            this.targetTableNames.pop();
            this.targetTableNames.push(targetPure);
            log.info("[Step 1] Target Table: {}", targetPure);
            if (x.getQuery() != null) {
                x.getQuery().accept(this);
            }
            return false;
        }

        /**
         * 处理 UNION/UNION ALL 结构，确保左右两边的 SELECT 都能被访问.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLUnionQuery x) {
            if (x.getLeft() != null) {
                x.getLeft().accept(this);
            }
            if (x.getRight() != null) {
                x.getRight().accept(this);
            }
            return false;
        }

        /**
         * 访问 FROM 中的物理表.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLExprTableSource x) {
            String tablePure = getTableNameOnly(x.getExpr().toString());
            String alias = x.getAlias() != null ? clean(x.getAlias()) : tablePure;
            this.aliasMaps.peek().put(alias, tablePure);
            return false;
        }

        /**
         * 处理 MaxCompute/Hive 的 LATERAL VIEW EXPLODE 语法.
         * 解决 rely_node_id 和 主表别名解析失败的核心逻辑.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLLateralViewTableSource x) {
            // 【核心修复】显式触发对原始表源（如 t2）的访问，确保其别名进入作用域。
            if (x.getTableSource() != null) {
                x.getTableSource().accept(this);
            }

            String tableAlias = clean(x.getAlias());
            List<String> colAliases = x.getColumns().stream()
                    .map(c -> clean(c.toString()))
                    .collect(Collectors.toList());

            // 提取 EXPLODE(...) 内部引用的字段
            List<String> refs = new ArrayList<>();
            if (x.getMethod() != null) {
                findRefs(x.getMethod(), refs);
            }

            Map<String, List<String>> exports = new HashMap<>();

            // 【增强回退逻辑】如果侧视图别名与现有别名冲突，先继承原有物理表的定义
            if (tableAlias != null && aliasMaps.peek().containsKey(tableAlias)) {
                String existingRef = aliasMaps.peek().get(tableAlias);
                if (existingRef != null && !existingRef.startsWith("SUBQUERY:")) {
                    List<String> physCols = metadataService.getColumns(existingRef);
                    if (physCols != null) {
                        for (String c : physCols) {
                            exports.put(c.toLowerCase(), Collections.singletonList(existingRef + "." + c.toLowerCase()));
                        }
                    }
                }
            }

            // LATERAL VIEW 产生的列（如 rely_node_id）依赖于其函数表达式引用的源字段
            for (String col : colAliases) {
                exports.put(col, new ArrayList<>(refs));
            }

            if (tableAlias != null) {
                // 使用唯一ID防止注册表重名覆盖
                String uniqueId = tableAlias + "_" + System.identityHashCode(x);
                aliasMaps.peek().put(tableAlias, "SUBQUERY:" + uniqueId);
                subqueryRegistry.put(uniqueId, exports);
                log.info("[Step 2.5] Lateral View Registered: {}, Columns: {}", tableAlias, colAliases);
            }
            return false;
        }

        /**
         * 访问 SELECT 列表中的每一项.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLSelectItem x) {
            String alias = clean(x.getAlias());
            SQLExpr expr = x.getExpr();

            List<String> refs = new ArrayList<>();
            findRefs(expr, refs);

            // 【核心修复】推导隐含别名逻辑。解决 CAST(is_ability AS ...) 没写别名时，自动提取字段名为 Key。
            String key = alias;
            if (key == null && refs.size() == 1) {
                String rawRef = refs.get(0);
                key = rawRef.contains(".") ? rawRef.substring(rawRef.lastIndexOf(".") + 1) : rawRef;
            }

            if (expr instanceof SQLAllColumnExpr) {
                SQLAllColumnExpr allExpr = (SQLAllColumnExpr) expr;
                key = (allExpr.getOwner() != null) ? clean(allExpr.getOwner().toString()) + ".*" : "*";
            }

            if (key == null) {
                key = clean(expr.toString());
            }

            // 记录当前列的表达式所引用的底层标识符
            this.selectItemSourcesMaps.peek().computeIfAbsent(key, k -> new ArrayList<>()).addAll(refs);
            return false;
        }

        /**
         * 处理 UNION 子查询（解决 thing_id 等字段在 UNION 结构下解析失败的问题）.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLUnionQueryTableSource x) {
            String rawAlias = x.getAlias();
            String alias = (rawAlias != null) ? clean(rawAlias) : "union_anon_" + System.identityHashCode(x);

            log.info("[Step 2] Entering Union Subquery: {}", alias);
            pushContext("sub_internal_" + alias);
            if (x.getUnion() != null) {
                x.getUnion().accept(this);
            }

            Map<String, List<String>> exports = new HashMap<>();
            Map<String, List<String>> internalSources = this.selectItemSourcesMaps.peek();

            // 【核心修复】处理 UNION 中的 * 展开逻辑，解决 t1.* 导致 Columns: 0 的问题
            expandStarInSubquery(exports);

            // 物理化并导出
            for (String colKey : internalSources.keySet()) {
                if (colKey.equals("*") || colKey.endsWith(".*")) {
                    continue;
                }
                Set<EdgeColumnInfoDto> phys = traceToPhysical(colKey, new HashSet<>());
                List<String> paths = phys.stream()
                        .map(p -> p.getTableName() + "." + p.getColumnName())
                        .collect(Collectors.toList());
                exports.put(colKey, paths);
            }

            popContext();
            String uniqueId = alias + "_" + System.identityHashCode(x);
            aliasMaps.peek().put(alias, "SUBQUERY:" + uniqueId);
            subqueryRegistry.put(uniqueId, exports);
            log.info("[Step 3] Union Subquery {} Exported, Columns: {}", alias, exports.size());
            return false;
        }

        /**
         * 处理子查询：这是解析多层嵌套的核心逻辑.
         * @param x x
         * @return boolean
         */
        @Override
        public boolean visit(SQLSubqueryTableSource x) {
            String rawAlias = x.getAlias();
            // 如果子查询是匿名的，则生成一个唯一标识
            String alias = (rawAlias != null) ? clean(rawAlias) : "anon_" + System.identityHashCode(x);

            log.info("[Step 2] Entering Subquery: {}", alias);
            pushContext("sub_internal_" + alias);
            x.getSelect().accept(this);

            Map<String, List<String>> exports = new HashMap<>();
            Map<String, List<String>> internalSources = this.selectItemSourcesMaps.peek();

            // 1. 处理子查询中的 * 展开
            expandStarInSubquery(exports);

            // 2. 物理化：在弹出栈之前，利用当前环境将字段解析为真实的"物理表.字段"
            for (String colKey : internalSources.keySet()) {
                if (colKey.equals("*") || colKey.endsWith(".*")) {
                    continue;
                }
                Set<EdgeColumnInfoDto> phys = traceToPhysical(colKey, new HashSet<>());
                List<String> paths = phys.stream()
                        .map(p -> p.getTableName() + "." + p.getColumnName())
                        .collect(Collectors.toList());

                String pureCol = colKey;
                if (colKey.contains(".")) {
                    pureCol = colKey.substring(colKey.lastIndexOf(".") + 1);
                }
                exports.put(pureCol, paths);
            }

            // 3. 销毁当前作用域，将物理化结果带回父级作用域
            popContext();
            String uniqueId = alias + "_" + System.identityHashCode(x);
            aliasMaps.peek().put(alias, "SUBQUERY:" + uniqueId);
            subqueryRegistry.put(uniqueId, exports);
            log.info("[Step 3] Subquery {} Exported, Columns: {}", alias, exports.size());
            return false;
        }

        /**
         * 将子查询内部的 SELECT * 展开为已知的列来源.
         * @param exports exports
         */
        private void expandStarInSubquery(Map<String, List<String>> exports) {
            Map<String, List<String>> internalSources = this.selectItemSourcesMaps.peek();
            List<String> stars = internalSources.keySet().stream()
                    .filter(k -> k.equals("*") || k.endsWith(".*"))
                    .collect(Collectors.toList());

            for (String starKey : stars) {
                String owner = starKey.contains(".") ? starKey.substring(0, starKey.indexOf(".")) : null;
                for (Map.Entry<String, String> entry : aliasMaps.peek().entrySet()) {
                    if (owner != null && !owner.equals(entry.getKey())) {
                        continue;
                    }
                    String refName = entry.getValue();
                    if (refName.startsWith("SUBQUERY:")) {
                        Map<String, List<String>> subFields = subqueryRegistry.get(refName.substring(9));
                        if (subFields != null) {
                            exports.putAll(subFields);
                        }

                    } else {
                        List<String> physCols = metadataService.getColumns(refName);
                        if (physCols != null) {
                            for (String c : physCols) {
                                exports.put(c.toLowerCase(), Collections.singletonList(refName + "." + c.toLowerCase()));
                            }
                        }
                    }
                }
                internalSources.remove(starKey);
            }
        }

        /**
         * QueryBlock 访问结束：此时执行最终的血缘映射.
         * @param x x
         */
        @Override
        public void endVisit(SQLSelectQueryBlock x) {
            String targetPure = this.targetTableNames.peek();
            // 跳过中间层子查询的虚拟目标，只在最终目标层生成血缘边
            if (targetPure == null || targetPure.startsWith("sub_internal_")) {
                return;
            }

            List<String> targetCols = this.metadataService.getColumns(targetPure);
            if (targetCols == null) {
                return;
            }

            String originalTargetName = restoreOriginalName(targetPure);
            List<SQLSelectItem> items = x.getSelectList();

            // 1. 处理 SELECT * 展开：将 * 转化为具体的列列表
            List<SQLExpr> expandedExprs = new ArrayList<>();
            for (SQLSelectItem item : items) {
                if (item.getExpr() instanceof SQLAllColumnExpr) {
                    expandStarToExprList(item.getExpr(), expandedExprs);
                } else {
                    expandedExprs.add(item.getExpr());
                }
            }

            log.info("[Step 4] Mapping columns for: {}, Expanded Select Size: {}", originalTargetName, expandedExprs.size());

            // 2. 基于位置索引（Positional Mapping）建立最终血缘
            for (int i = 0; i < expandedExprs.size() && i < targetCols.size(); i++) {
                String tCol = targetCols.get(i).toLowerCase();
                SQLExpr currentExpr = expandedExprs.get(i);

                List<String> refs = new ArrayList<>();
                findRefs(currentExpr, refs);

                Set<EdgeColumnInfoDto> sources = new HashSet<>();
                for (String r : refs) {
                    sources.addAll(traceToPhysical(r, new HashSet<>()));
                }

                if (!sources.isEmpty()) {
                    EdgeTableColumnDto edge = new EdgeTableColumnDto();
                    edge.setTargetColumnInfo(new EdgeColumnInfoDto().setTableName(originalTargetName).setColumnName(tCol));
                    edge.setSourceColumnInfos(new ArrayList<>(sources));
                    this.lineageEdges.add(edge);
                    String logMsg = "  [SUCCESS] " + tCol + " <- " + sources;
                    this.parseLogs.add(logMsg);
                    log.info(logMsg);
                } else {
                    // 【优化修复】补全打印语句，确保所有派生/常量字段日志可见，解决字段丢失问题。
                    if (refs.isEmpty() || (currentExpr instanceof SQLLiteralExpr)) {
                        String logMsg = "  [SUCCESS] " + tCol + " <- [CONSTANT/LITERAL]";
                        this.parseLogs.add(logMsg);
                        log.info(logMsg);
                    } else if (isReferenceDefined(currentExpr, refs)) {
                        String logMsg = "  [SUCCESS] " + tCol + " <- [DERIVED] " + currentExpr;
                        this.parseLogs.add(logMsg);
                        log.info(logMsg);
                    } else {
                        String logMsg = "  [FAILURE] " + tCol + " traceable source not found. Expr: " + currentExpr;
                        this.parseLogs.add(logMsg);
                        log.warn(logMsg);
                    }
                }
            }
        }

        /**
         * 校验引用标识符是否在当前作用域内有明确定义.
         * @param expr expr
         * @param refs refs
         * @return boolean
         */
        private boolean isReferenceDefined(SQLExpr expr, List<String> refs) {
            for (String ref : refs) {
                String cleanedRef = clean(ref);
                String owner = cleanedRef.contains(".") ? cleanedRef.substring(0, cleanedRef.lastIndexOf(".")) : null;
                String col = cleanedRef.contains(".") ? cleanedRef.substring(cleanedRef.lastIndexOf(".") + 1) : cleanedRef;

                boolean found = false;
                if (owner != null) {
                    String mapped = this.aliasMaps.peek().get(owner);
                    if (mapped != null && mapped.startsWith("SUBQUERY:")) {
                        Map<String, List<String>> exports = this.subqueryRegistry.get(mapped.substring(9));
                        if (exports != null && exports.containsKey(col)) {
                            found = true;
                        }

                        // 回退逻辑：处理别名遮蔽物理表情况
                        if (!found) {
                            String ownerPure = getTableNameOnly(owner);
                            if (metadataService.tableExists(ownerPure) && metadataService.getColumns(ownerPure).contains(col)) {
                                found = true;
                            }
                        }
                    } else if (mapped != null) {
                        List<String> columns = metadataService.getColumns(getTableNameOnly(mapped));
                        if (columns != null && columns.contains(col)) {
                            found = true;
                        }
                    }
                } else {
                    for (String val : this.aliasMaps.peek().values()) {
                        if (val.startsWith("SUBQUERY:")) {
                            Map<String, List<String>> ex = subqueryRegistry.get(val.substring(9));
                            if (ex != null && ex.containsKey(col)) {
                                found = true;
                                break;
                            }
                        } else {
                            List<String> columns = metadataService.getColumns(getTableNameOnly(val));
                            if (columns != null && columns.contains(col)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 将表达式中的 SELECT * 转换为具体的字段引用列表.
         * @param starExpr starExpr
         * @param targetList targetList
         */
        private void expandStarToExprList(SQLExpr starExpr, List<SQLExpr> targetList) {
            String owner = (starExpr instanceof SQLPropertyExpr) ? clean(((SQLPropertyExpr) starExpr).getOwnerName()) : null;

            for (Map.Entry<String, String> entry : aliasMaps.peek().entrySet()) {
                if (owner != null && !owner.equals(entry.getKey())) {
                    continue;
                }
                String ref = entry.getValue();
                Collection<String> cols = ref.startsWith("SUBQUERY:")
                        ? subqueryRegistry.getOrDefault(ref.substring(9), Collections.emptyMap()).keySet()
                        : metadataService.getColumns(ref);

                if (cols != null) {
                    for (String c : cols) {
                        targetList.add(new SQLPropertyExpr(new SQLIdentifierExpr(entry.getKey()), c));
                    }
                }
            }
        }

        /**
         * 将表达式解析为物理表列集合.
         * @param expr expr
         * @return set
         */
        private Set<EdgeColumnInfoDto> resolveExprToPhysical(SQLExpr expr) {
            List<String> refs = new ArrayList<>();
            findRefs(expr, refs);
            Set<EdgeColumnInfoDto> res = new HashSet<>();
            for (String r : refs) {
                res.addAll(traceToPhysical(r, new HashSet<>()));
            }
            return res;
        }

        /**
         * 深度递归抓取表达式中的所有列名.
         * @param expr expr
         * @param refs refs
         */
        private void findRefs(SQLExpr expr, List<String> refs) {
            if (expr == null || expr instanceof SQLLiteralExpr) {
                return;
            }
            if (expr instanceof SQLIdentifierExpr || expr instanceof SQLPropertyExpr) {
                String val = clean(expr.toString());
                // 【核心修复】过滤 SQL 内置关键字
                if (!"current_date".equals(val) && !"current_timestamp".equals(val)
                        && !"now".equals(val) && !"sysdate".equals(val) && !"null".equals(val)) {
                    refs.add(val);
                }
            } else if (expr instanceof SQLArrayExpr) {
                findRefs(((SQLArrayExpr) expr).getExpr(), refs);
            } else if (expr instanceof SQLAggregateExpr) {
                SQLAggregateExpr agg = (SQLAggregateExpr) expr;
                for (SQLExpr arg : agg.getArguments()) {
                    findRefs(arg, refs);
                }
                if (agg.getOver() != null) {
                    SQLOver over = agg.getOver();
                    for (SQLExpr p : over.getPartitionBy()) {
                        findRefs(p, refs);
                    }
                    if (over.getOrderBy() != null) {
                        for (SQLSelectOrderByItem item : over.getOrderBy().getItems()) {
                            findRefs(item.getExpr(), refs);
                        }
                    }
                }
            } else if (expr instanceof SQLMethodInvokeExpr) {
                SQLMethodInvokeExpr method = (SQLMethodInvokeExpr) expr;
                // 【核心修复】支持方法链式调用的所有者追溯 (如 t3.node_ids.size())
                if (method.getOwner() != null) {
                    findRefs(method.getOwner(), refs);
                }
                for (SQLExpr a : method.getArguments()) {
                    findRefs(a, refs);
                }
            } else if (expr instanceof SQLBinaryOpExpr) {
                findRefs(((SQLBinaryOpExpr) expr).getLeft(), refs);
                findRefs(((SQLBinaryOpExpr) expr).getRight(), refs);
            } else if (expr instanceof SQLCaseExpr) {
                SQLCaseExpr c = (SQLCaseExpr) expr;
                for (SQLCaseExpr.Item it : c.getItems()) {
                    findRefs(it.getConditionExpr(), refs);
                    findRefs(it.getValueExpr(), refs);
                }
                findRefs(c.getElseExpr(), refs);
            } else if (expr instanceof SQLCastExpr) {
                findRefs(((SQLCastExpr) expr).getExpr(), refs);
            }
        }

        /**
         * 核心递归追溯逻辑：追溯一个标识符直至物理表.
         * @param ref ref
         * @param visited visited
         * @return set
         */
        private Set<EdgeColumnInfoDto> traceToPhysical(String ref, Set<String> visited) {
            Set<EdgeColumnInfoDto> res = new HashSet<>();
            if (ref == null || visited.contains(ref)) {
                return res;
            }
            visited.add(ref);

            String owner = ref.contains(".") ? ref.substring(0, ref.lastIndexOf(".")) : null;
            String col = ref.contains(".") ? ref.substring(ref.lastIndexOf(".") + 1) : ref;

            if (owner != null) {
                // 1. 跨层级追溯：如果是物理表
                String ownerPure = getTableNameOnly(owner);
                if (this.metadataService.tableExists(ownerPure)) {
                    res.add(new EdgeColumnInfoDto().setTableName(restoreOriginalName(ownerPure)).setColumnName(col));
                    return res;
                }
                // 2. 追溯别名
                String mapped = this.aliasMaps.peek().get(owner);
                if (mapped != null) {
                    if (mapped.startsWith("SUBQUERY:")) {
                        Map<String, List<String>> exports = this.subqueryRegistry.get(mapped.substring(9));
                        if (exports != null && exports.containsKey(col)) {
                            for (String s : exports.get(col)) {
                                res.addAll(traceToPhysical(s, visited));
                            }
                        }
                        // 回退机制
                        if (res.isEmpty()) {
                            String oPure = getTableNameOnly(owner);
                            if (this.metadataService.tableExists(oPure)) {
                                List<String> columns = this.metadataService.getColumns(oPure);
                                if (columns != null && columns.contains(col)) {
                                    res.add(new EdgeColumnInfoDto().setTableName(restoreOriginalName(oPure)).setColumnName(col));
                                }
                            }
                        }
                    } else {
                        String mPure = getTableNameOnly(mapped);
                        if (this.metadataService.tableExists(mPure)) {
                            res.add(new EdgeColumnInfoDto().setTableName(restoreOriginalName(mPure)).setColumnName(col));
                        }
                    }
                }
            } else {
                List<String> nexts = this.selectItemSourcesMaps.peek().get(col);
                List<String> recursiveRefs = nexts == null ? Collections.emptyList()
                        : nexts.stream().filter(n -> !n.equalsIgnoreCase(col)).collect(Collectors.toList());

                if (!recursiveRefs.isEmpty()) {
                    for (String s : recursiveRefs) {
                        res.addAll(traceToPhysical(s, visited));
                    }
                } else {
                    for (Map.Entry<String, String> entry : this.aliasMaps.peek().entrySet()) {
                        String t = entry.getValue();
                        if (t.startsWith("SUBQUERY:")) {
                            Map<String, List<String>> ex = subqueryRegistry.get(t.substring(9));
                            if (ex != null && ex.containsKey(col)) {
                                for (String s : ex.get(col)) {
                                    res.addAll(traceToPhysical(s, visited));
                                }
                                if (!res.isEmpty()) {
                                    break;
                                }
                            }
                        } else {
                            String tP = getTableNameOnly(t);
                            if (this.metadataService.tableExists(tP)) {
                                List<String> columns = this.metadataService.getColumns(tP);
                                if (columns != null && columns.contains(col)) {
                                    res.add(new EdgeColumnInfoDto().setTableName(restoreOriginalName(tP)).setColumnName(col));
                                    if (!res.isEmpty()) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return res;
        }
    }

    /**
     * 入口：解析 SQL 边血缘.
     * @param sql sql
     * @param dbType dbType
     * @param tableColumnInfos tableColumnInfos
     * @return list
     */
    public static DruidParseResultDto parseEdges(String sql, String dbType, Map<String, List<DataSourceTableColumnDto>> tableColumnInfos) {
        DruidMetaDataServiceImpl metadataService = new DruidMetaDataServiceImpl();
        Map<String, String> pureToOriginalNameMap = new HashMap<>();

        tableColumnInfos.forEach((k, v) -> {
            Map<String, String> cols = new LinkedHashMap<>();
            String pureTable = k.contains(".") ? k.substring(k.lastIndexOf(".") + 1) : k;
            pureTable = pureTable.replace("`", "").toLowerCase();
            pureToOriginalNameMap.put(pureTable, k);
            v.forEach(c -> cols.put(c.getColumnName().toLowerCase(), c.getColumnType()));
            metadataService.registerTable(pureTable, cols);
        });

        String finalDbType = dbType.equalsIgnoreCase("maxcompute") ? "odps" : dbType;
        List<SQLStatement> stmts = SQLUtils.parseStatements(sql, finalDbType);

        Set<EdgeTableColumnDto> allEdges = new HashSet<>();
        List<String> allLogs = new ArrayList<>();
        for (SQLStatement stmt : stmts) {
            LineageVisitor visitor = new LineageVisitor(metadataService, pureToOriginalNameMap);
            stmt.accept(visitor);
            allEdges.addAll(visitor.getLineageEdges());
            allLogs.addAll(visitor.getParseLogs());
        }
        return new DruidParseResultDto(new ArrayList<>(allEdges), allLogs);
    }
}

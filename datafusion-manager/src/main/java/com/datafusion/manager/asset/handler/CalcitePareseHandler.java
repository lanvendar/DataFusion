package com.datafusion.manager.asset.handler;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.asset.dto.EdgeTableColumnDto;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.EdgeColumnInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalSort;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rel.metadata.RelColumnOrigin;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.schema.AggregateFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/30
 * @since 2025/10/30
 */
@Slf4j
public class CalcitePareseHandler {

    /**
     * 定义常见的 SQL 关键字，防止被误识别为表别名.
     */
    private static final Set<String> SQL_RESERVED_KEYWORDS = new HashSet<>(Arrays.asList(
            "where", "group", "order", "limit", "on", "join", "left", "right",
            "inner", "outer", "union", "having", "select", "and", "or", "as"));

    /**
     * 进行表注册,以及初始化解析器.
     *
     * @param sql              sql信息
     * @param tableColumnInfos 元数据信息
     * @return 返回planner
     */
    public static List<EdgeTableColumnDto> parseSql(String sql, Map<String, List<DataSourceTableColumnDto>> tableColumnInfos)
            throws ValidationException {
        Planner planner = registeAndInitPlanner(tableColumnInfos);
        SqlNode sqlNode = null;
        RelRoot relRoot = null;
        // 先预处理 SQL，修复各种语法问题
        sql = fixInvalidIdentifiers(sql);
        log.info("[parse]" + sql);
        try {
            sqlNode = planner.parse(sql);
        } catch (SqlParseException e) {
            e.printStackTrace();
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "planner解析sql失败,失败原因:" + e.getMessage());
        }

        // 应用 AST 重写：处理多参数 CONCAT
        // Calcite 内置 CONCAT 只支持 2 个参数，将多参数 CONCAT 转换为嵌套调用
        SqlNode rewrittenNode = SqlAstRewriter.rewriteConcat(sqlNode);
        // rewrittenNode = SqlAstRewriter.rewriteIf(rewrittenNode); // 暂时禁用，排查 IndexOutOfBoundsException

        // NVL -> COALESCE 转换
        rewrittenNode = SqlAstRewriter.rewriteNvlToCoalesce(rewrittenNode);

        // DATE/DATETIME -> TIMESTAMP 转换
        rewrittenNode = SqlAstRewriter.forceCastToTimestamp(rewrittenNode);

        // PostgreSQL :: 类型转换 -> CAST 转换
        rewrittenNode = SqlAstRewriter.rewriteDoubleColonToCast(rewrittenNode);

        // 减法操作符 -> BIGINT 类型转换
        rewrittenNode = SqlAstRewriter.rewriteMinusToBigint(rewrittenNode);

        // json_agg -> JSON_ARRAY, json_object_agg -> JSON_OBJECT 转换
        //rewrittenNode = SqlAstRewriter.rewriteJsonAggToJsonArray(rewrittenNode);

        // TO_CHAR -> CAST(expr AS VARCHAR) 转换
        //rewrittenNode = SqlAstRewriter.rewriteToCharToCast(rewrittenNode);

        try {
            // 先尝试直接 validate
            SqlNode validatedSqlNode = null;
            validatedSqlNode = planner.validate(rewrittenNode);
            // 在 rel 之前，修复 CASE WHEN 中的 NULL 类型
            validatedSqlNode = SqlAstRewriter.fixNullTypeInCaseWhen(validatedSqlNode);
            relRoot = planner.rel(validatedSqlNode);
        } catch (RelConversionException e) {
            e.printStackTrace();
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "planner获取relRoot失败,失败原因:" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "planner获取relRoot失败,未知异常"+e.getMessage());
        } catch (Error e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "planner获取relRoot失败," + e.getMessage());
        }

        RelNode relNode = relRoot.rel;
        List<EdgeTableColumnDto> edgeDtos = new ArrayList<>();
        parseRelNode(relNode, tableColumnInfos, edgeDtos);
        planner.close();
        return edgeDtos;
    }

    /**
     * 进行表注册,以及初始化解析器.
     *
     * @param tableColumns 元数据信息
     * @return 返回planner
     */
    public static Planner registeAndInitPlanner(Map<String, List<DataSourceTableColumnDto>> tableColumns) {
        Map<String, CustomSchema> registedSchema = new HashMap<>();
        SchemaPlus rootSchema = CalciteSchema.createRootSchema(true).plus();
        RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
        //挂在根目录下的表没有schema
        for (Map.Entry<String, List<DataSourceTableColumnDto>> entry : tableColumns.entrySet()) {
            String tableName = entry.getKey();
            if (tableName.contains(".")) {
                String schemaName = tableName.split("\\.")[0];
                tableName = tableName.split("\\.")[1];
                if (registedSchema.get(schemaName) == null) {
                    CustomSchema customSchema = new CustomSchema(schemaName);
                    registedSchema.put(schemaName, customSchema);
                    rootSchema.add(schemaName, customSchema);
                }
                RelDataTypeFactory.Builder builder = new RelDataTypeFactory.Builder(typeFactory);
                for (DataSourceTableColumnDto columnInfoEntity : entry.getValue()) {
                    builder.add(columnInfoEntity.getColumnName(), createRelDataType(typeFactory, columnInfoEntity));
                }
                registedSchema.get(schemaName).addTable(tableName, new SimpleTable(builder.build()));
            } else {
                RelDataTypeFactory.Builder builder = new RelDataTypeFactory.Builder(typeFactory);
                for (DataSourceTableColumnDto columnInfoEntity : entry.getValue()) {
                    builder.add(columnInfoEntity.getColumnName(), createRelDataType(typeFactory, columnInfoEntity));
                }
                rootSchema.add(entry.getKey(), new SimpleTable(builder.build()));
            }

        }

        // 定义聚合函数名称集合（需要注册为聚合函数的方法名）
        Set<String> aggregateFunctionNames = new LinkedHashSet<>();
        aggregateFunctionNames.add("max_by");
        aggregateFunctionNames.add("min_by");
        aggregateFunctionNames.add("collect_list");
        aggregateFunctionNames.add("collect_set");
        aggregateFunctionNames.add("string_agg");
        aggregateFunctionNames.add("first_value");
        aggregateFunctionNames.add("last_value");
        aggregateFunctionNames.add("wm_concat");
        aggregateFunctionNames.add("json_agg");
        aggregateFunctionNames.add("jsonb_array_elements");
        // json_object_agg 是 Calcite 内置函数，无需自定义注册

        // 定义需要明确返回类型的聚合函数（函数名 -> 返回类型）
        Map<String, SqlTypeName> aggregateReturnTypes = new HashMap<>();
        aggregateReturnTypes.put("string_agg", SqlTypeName.VARCHAR); // string_agg 返回 VARCHAR
        // collect_set 和 collect_list 返回 ARRAY<VARCHAR>
        aggregateReturnTypes.put("collect_list", SqlTypeName.ARRAY);
        aggregateReturnTypes.put("collect_set", SqlTypeName.ARRAY);
        // first_value 和 last_value 返回与输入参数相同的类型
        aggregateReturnTypes.put("first_value", SqlTypeName.ANY);
        aggregateReturnTypes.put("last_value", SqlTypeName.ANY);
        // json_agg 返回 VARCHAR（JSON 字符串）
        aggregateReturnTypes.put("json_agg", SqlTypeName.VARCHAR);
        // jsonb_array_elements 返回 VARCHAR（展开后的元素）
        aggregateReturnTypes.put("jsonb_array_elements", SqlTypeName.VARCHAR);
        // json_object_agg 是 Calcite 内置函数，无需配置返回类型
        // wm_concat 返回 VARCHAR
        aggregateReturnTypes.put("wm_concat", SqlTypeName.VARCHAR);
        // max_by 和 min_by 使用默认的 ANY 类型（因为返回类型取决于第一个参数）

        // 记录已注册的聚合函数，避免重复注册
        Set<String> registeredAggregateFunctions = new LinkedHashSet<>();

        // 收集自定义标量函数，用于创建自定义 OperatorTable
        List<SqlOperator> customScalarOperators = new ArrayList<>();

        // 注册自定义函数（标量函数和聚合函数）
        for (Method method : CustomeSqlFunction.class.getDeclaredMethods()) {
            // 检查方法是否是 public 和 static
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                String methodName = method.getName();
                String functionNameUpper = methodName.toUpperCase();
                String functionNameLower = methodName.toLowerCase();

                // 判断是聚合函数还是标量函数
                if (aggregateFunctionNames.contains(methodName)) {
                    // 聚合函数：使用通用包装类，每个函数只注册一次（避免重载导致重复注册）
                    if (!registeredAggregateFunctions.contains(functionNameUpper)) {
                        // 获取该聚合函数的返回类型，默认为 ANY
                        SqlTypeName returnType = aggregateReturnTypes.getOrDefault(methodName, SqlTypeName.ANY);
                        AggregateFunction aggregateFunction = new GenericAggregateFunction(method, functionNameUpper, returnType);
                        rootSchema.add(functionNameUpper, aggregateFunction);
                        registeredAggregateFunctions.add(functionNameUpper);
                        log.debug("注册自定义聚合函数: {} -> 返回类型: {}", functionNameUpper, returnType);
                    }
                } else {
                    // 注册为标量函数（支持方法重载）
                    ScalarFunction scalarFunction = ScalarFunctionImpl.create(method);
                    // 同时注册大写和小写版本，以支持 `func` 和 FUNC 两种调用方式
                    rootSchema.add(functionNameUpper, scalarFunction);
                    if (!functionNameUpper.equals(functionNameLower)) {
                        rootSchema.add(functionNameLower, scalarFunction);
                        log.debug("注册自定义标量函数: {} 和 {} -> {}", functionNameUpper, functionNameLower, method);
                    } else {
                        log.debug("注册自定义标量函数: {} -> {}", functionNameUpper, method);
                    }
                }
            }
        }

        SqlParser.Config parserConfig = SqlParser.config()
                .withParserFactory(SqlBabelParserImpl.FACTORY)
                .withConformance(SqlConformanceEnum.BABEL)
                .withUnquotedCasing(Casing.UNCHANGED)
                .withLex(Lex.BIG_QUERY)
                .withCaseSensitive(false);

        // 直接使用 SqlStdOperatorTable
        // Calcite 会自动从 defaultSchema (rootSchema) 中查找自定义函数
        // 如果找到同名函数会优先使用，找不到再去 SqlStdOperatorTable 中查找
        SqlOperatorTable opTable =
                SqlLibraryOperatorTableFactory.INSTANCE
                        .getOperatorTable(
                                SqlLibrary.STANDARD,
                                SqlLibrary.MYSQL,
                                SqlLibrary.POSTGRESQL,
                                SqlLibrary.HIVE
                        );

        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(rootSchema)
                .parserConfig(parserConfig)
                .operatorTable(opTable)
                .build();
        return Frameworks.getPlanner(config);
    }

    /**
     * 通过relNode获取血缘.
     *
     * @param relNode     血缘
     * @param tableColums 表名称集合
     * @param edgeDtos    血缘收集
     */
    public static void parseRelNode(RelNode relNode, Map<String, List<DataSourceTableColumnDto>> tableColums,
            List<EdgeTableColumnDto> edgeDtos) {
        String targetTableName = null;
        if (relNode instanceof LogicalTableModify) {
            LogicalTableModify tableModify = (LogicalTableModify) relNode;
            if (tableModify.getTable().getQualifiedName().size() == 2) {
                targetTableName = String.join(".", tableModify.getTable().getQualifiedName()).toLowerCase();
            } else {
                targetTableName = tableModify.getTable().getQualifiedName()
                        .get(tableModify.getTable().getQualifiedName().size() - 1);
            }
            // 对于 INSERT 语句，我们需要处理它的输入，即 SELECT 语句的 RelNode
            traverseRelNode(tableModify.getInput(), targetTableName, edgeDtos, tableColums);
        } else if (relNode instanceof LogicalProject) { // SELECT 语句的根通常是 LogicalProject
            // 对于纯 SELECT 语句，没有明确的目标表，可以设置为临时表名或不设置
            // 或者根据业务逻辑，将其视为一个虚拟输出
            // 这里我们假设它最终会流向某个“结果集”，暂不设置 targetTableName 或用一个默认值
            // 如果是 CREATE TABLE AS SELECT，则 targetTableName 应该在外部逻辑中获取
            traverseRelNode(relNode, "query_result", edgeDtos, tableColums); // 示例：使用 "query_result" 作为目标表名
        } else {
            // 处理其他类型的根节点，例如 Union, Sort等，继续向下遍历
            traverseRelNode(relNode, "query_result", edgeDtos, tableColums);
        }
    }

    /**
     * 递归遍历 RelNode 树，提取血缘信息.
     *
     * @param relNode         当前正在处理的 RelNode
     * @param targetTableName 目标表名（对于 INSERT 语句）+
     * @param edgeDtos        存储血缘边的列表
     * @param tableColums     元数据信息
     */
    public static void traverseRelNode(RelNode relNode, String targetTableName, List<EdgeTableColumnDto> edgeDtos,
            Map<String, List<DataSourceTableColumnDto>> tableColums) {
        RelMetadataQuery mq = relNode.getCluster().getMetadataQuery();
        if (relNode instanceof LogicalProject) {
            LogicalProject project = (LogicalProject) relNode;
            List<RexNode> projectExpressions = project.getProjects();
            for (int i = 0; i < projectExpressions.size(); i++) {
                RexNode projectExpr = projectExpressions.get(i);
                //insert 语句,targetColumnName可以直接从元数据中获取
                InputRefVisitor visitor = new InputRefVisitor();
                projectExpr.accept(visitor);
                EdgeTableColumnDto edgeDto = new EdgeTableColumnDto();
                //判断targetName是否在元数据中
                String newTargetTableName = null;
                if (!tableColums.containsKey(targetTableName)) {
                    if (targetTableName.contains(".")) {
                        newTargetTableName = targetTableName.split("\\.")[1];
                    }
                } else {
                    newTargetTableName = targetTableName;
                }
                edgeDto.setTargetColumnInfo(
                        new EdgeColumnInfoDto()
                                .setTableName(targetTableName)
                                .setColumnName(tableColums.get(newTargetTableName).get(i).getColumnName()));
                edgeDtos.add(edgeDto);
                List<EdgeColumnInfoDto> columnInfoDtos = new ArrayList<>();
                for (Integer inputIndex : visitor.getInputIndices()) {
                    RelMetadataQuery metadataQuery = relNode.getCluster().getMetadataQuery();
                    Set<RelColumnOrigin> origins = metadataQuery.getColumnOrigins(relNode.getInput(0), inputIndex);
                    edgeDto.setSourceColumnInfos(columnInfoDtos);
                    if (origins != null && !origins.isEmpty()) {
                        for (RelColumnOrigin origin : origins) {
                            List<String> tableNameParts = origin.getOriginTable().getQualifiedName();
                            int columnIndex = origin.getOriginColumnOrdinal();
                            // 通过索引获取字段名
                            String columnName = origin.getOriginTable().getRowType().getFieldNames().get(columnIndex);
                            // 拼接成 "schema.table.column" 格式
                            String sourceTable = String.join(".", tableNameParts).toLowerCase();
                            columnInfoDtos.add(
                                    new EdgeColumnInfoDto()
                                            .setColumnName(columnName)
                                            .setTableName(sourceTable));
                        }
                    }

                }
            }
            /*String targetColumnName = tableColums.get(targetTableName).get(i).getColumnName();
            EdgeTableColumnDto edgeTableColumnDto = new EdgeTableColumnDto();
            edgeTableColumnDto.setTargetColumnInfo(
                    new EdgeColumnInfoDto()
                            .setColumnName(targetColumnName)
                            .setTableName(targetTableName) // 目标表名由外部传入
            );
            edgeDtos.add(edgeTableColumnDto);
            
            edgeTableColumnDto.setSourceColumnInfos(columnInfoDtos);
            // 获取当前投影列的所有原始来源
            Set<RelColumnOrigin> columnOrigins = mq.getColumnOrigins(project, i);
            if (!CollectionUtils.isEmpty(columnOrigins)) {
                for (RelColumnOrigin origin : columnOrigins) {
                    List<String> qualifiedNames = origin.getOriginTable().getQualifiedName();
                    qualifiedNames = qualifiedNames.stream().map(x -> x.toLowerCase()).collect(Collectors.toList());
                    String sourceTableName = String.join(".", qualifiedNames);
                    int columnIndex = origin.getOriginColumnOrdinal();
                    // 通过索引获取字段名
                    String sourceColumnName = origin.getOriginTable().getRowType().getFieldNames().get(columnIndex);
                    columnInfoDtos.add(
                            new EdgeColumnInfoDto()
                                    .setColumnName(sourceColumnName)
                                    .setTableName(sourceTableName)
                    );
                }
            } else {
                // 如果 RelMetadataQuery 没有直接提供来源 (例如，复杂的表达式，或者聚合函数)
                // 需要进一步递归分析 projectExpr 表达式本身来寻找其引用的源列
                // 但 RelMetadataQuery 应该是首选且更准确的方式
                log.warn("Could not get column origins for project expression: {} at index {} in {}",
                        projectExpr, i, project);
                // 可以添加 Fallback 逻辑，例如手动遍历 RexNode 的子节点
                // current implementation relies on mq.getColumnOrigins for comprehensive source column info
            }
            }
            // 递归处理 Project 节点的输入
            traverseRelNode(project.getInput(), targetTableName, edgeDtos, tableColums);*/

        } else if (relNode instanceof LogicalFilter) {
            LogicalFilter filter = (LogicalFilter) relNode;
            // 过滤器通常不直接影响列血缘，但其输入会影响结果集
            traverseRelNode(filter.getInput(), targetTableName, edgeDtos, tableColums);

        } else if (relNode instanceof LogicalJoin) {
            LogicalJoin join = (LogicalJoin) relNode;
            // JOIN 节点会有左右两个输入
            traverseRelNode(join.getLeft(), targetTableName, edgeDtos, tableColums);
            traverseRelNode(join.getRight(), targetTableName, edgeDtos, tableColums);

        } else if (relNode instanceof LogicalUnion) {
            LogicalUnion union = (LogicalUnion) relNode;
            // 递归处理 UNION 的每个输入子查询
            for (RelNode input : union.getInputs()) {
                traverseRelNode(input, targetTableName, edgeDtos, tableColums);
            }

        } else if (relNode instanceof LogicalAggregate) {
            LogicalAggregate aggregate = (LogicalAggregate) relNode;
            // 聚合操作会改变列的结构，但对于 GROUP BY 查询，输出的列直接对应 GROUP BY 的列
            // 需要先提取 Aggregate 输出列的血缘信息，再递归处理输入
            extractAggregateColumnLineage(aggregate, targetTableName, edgeDtos, tableColums);
            traverseRelNode(aggregate.getInput(), targetTableName, edgeDtos, tableColums);

        } else if (relNode instanceof LogicalSort) {
            LogicalSort sort = (LogicalSort) relNode;
            // 排序通常不改变列的血缘
            traverseRelNode(sort.getInput(), targetTableName, edgeDtos, tableColums);

        } else if (relNode instanceof LogicalTableScan) {
            LogicalTableScan tableScan = (LogicalTableScan) relNode;
            // 这是一个源表的扫描节点。
            // 此时，我们不需要在这里直接创建新的 edgeDtos，
            // 因为 `mq.getColumnOrigins` 会从最底层（即 TableScan）回溯到原始列。
            // 这个节点的目的是为上层节点提供原始的表和列信息。
            log.debug("Encountered LogicalTableScan for table: {}", tableScan.getTable().getQualifiedName());

        } else if (relNode instanceof LogicalTableModify) {
            // 这个分支通常会在 parseLineage 方法的顶层处理，以获取 targetTableName。
            // 如果在这里再次遇到，说明是嵌套的修改，通常不常见。
            // 确保处理其输入（SELECT 语句的 RelNode）
            LogicalTableModify tableModify = (LogicalTableModify) relNode;
            traverseRelNode(tableModify.getInput(), targetTableName, edgeDtos, tableColums);

        } else {
            // 处理其他未知类型的节点，递归处理其所有输入（如果存在）
            if (!relNode.getInputs().isEmpty()) {
                log.warn("Unknown RelNode type encountered: {}. Recursively processing inputs.", relNode.getClass().getSimpleName());
                for (RelNode input : relNode.getInputs()) {
                    traverseRelNode(input, targetTableName, edgeDtos, tableColums);
                }
            }
        }
    }

    /**
     * 从 LogicalAggregate 节点提取列血缘信息.
     * 对于 GROUP BY 查询，输出的列直接对应 GROUP BY 的列（group keys）.
     * 这些列的血缘需要通过 getColumnOrigins 或直接映射来获取.
     *
     * @param aggregate      Aggregate 节点
     * @param targetTableName 目标表名
     * @param edgeDtos       血缘边列表
     * @param tableColums    元数据信息
     */
    private static void extractAggregateColumnLineage(LogicalAggregate aggregate, String targetTableName,
            List<EdgeTableColumnDto> edgeDtos, Map<String, List<DataSourceTableColumnDto>> tableColums) {
        RelMetadataQuery mq = aggregate.getCluster().getMetadataQuery();
        int outputColumnCount = aggregate.getRowType().getFieldList().size();

        // 获取输入表的字段列表
        RelNode input = aggregate.getInput();
        List<RelDataTypeField> inputFields = input.getRowType().getFieldList();
        for (int i = 0; i < outputColumnCount; i++) {
            EdgeTableColumnDto edgeDto = new EdgeTableColumnDto();
            String newTargetTableName = null;
            if (!tableColums.containsKey(targetTableName)) {
                if (targetTableName.contains(".")) {
                    newTargetTableName = targetTableName.split("\\.")[1];
                }
            } else {
                newTargetTableName = targetTableName;
            }

            // 设置目标列信息
            if (tableColums.containsKey(newTargetTableName) && i < tableColums.get(newTargetTableName).size()) {
                edgeDto.setTargetColumnInfo(
                        new EdgeColumnInfoDto()
                                .setTableName(targetTableName)
                                .setColumnName(tableColums.get(newTargetTableName).get(i).getColumnName()));
            }

            List<EdgeColumnInfoDto> columnInfoDtos = new ArrayList<>();
            edgeDto.setSourceColumnInfos(columnInfoDtos);
            // 尝试通过 getColumnOrigins 获取列来源
            Set<RelColumnOrigin> origins = mq.getColumnOrigins(aggregate, i);
            if (origins != null && !origins.isEmpty()) {
                // getColumnOrigins 成功获取到来源
                for (RelColumnOrigin origin : origins) {
                    List<String> tableNameParts = origin.getOriginTable().getQualifiedName();
                    int columnIndex = origin.getOriginColumnOrdinal();
                    String columnName = origin.getOriginTable().getRowType().getFieldNames().get(columnIndex);
                    String sourceTable = String.join(".", tableNameParts).toLowerCase();
                    columnInfoDtos.add(
                            new EdgeColumnInfoDto()
                                    .setColumnName(columnName)
                                    .setTableName(sourceTable));
                }
            } else {
                // getColumnOrigins 返回空，尝试 fallback
                // 对于 GROUP BY 查询，输出的第 i 列对应 group key 的第 i 列
                // group key 的位置可以通过 aggregate 的 groupSets 来确定
                // 但更简单的方法是：假设 GROUP BY 列在输入表中按顺序对应
                if (i < inputFields.size()) {
                    // 通过输入表的同名列来建立血缘
                    String inputColumnName = inputFields.get(i).getName();
                    String sourceTable = input.getRowType().getFieldNames().get(i);
                    // 尝试获取输入表的完整名称
                    if (input instanceof LogicalTableScan) {
                        List<String> qualifiedName = ((LogicalTableScan) input).getTable().getQualifiedName();
                        sourceTable = String.join(".", qualifiedName).toLowerCase();
                    }
                    columnInfoDtos.add(
                            new EdgeColumnInfoDto()
                                    .setColumnName(inputColumnName)
                                    .setTableName(sourceTable));
                }
            }

            edgeDtos.add(edgeDto);
        }
    }

    /**
     * 一个 RexVisitor，用于收集一个 RexNode 表达式中所有作为输入的字段引用 (RexInputRef).
     */
    public static class InputRefVisitor extends RexVisitorImpl<Void> {

        /**
         * 输入索引.
         */
        private final Set<Integer> inputIndices = new LinkedHashSet<>();

        /**
         * 构造方法.
         */
        protected InputRefVisitor() {
            // true 表示深入访问，会进入 RexCall 的内部
            super(true);
        }

        public Set<Integer> getInputIndices() {
            return inputIndices;
        }

        @Override
        public Void visitInputRef(RexInputRef inputRef) {
            // 当访问到一个输入引用时，记录下它的索引
            inputIndices.add(inputRef.getIndex());
            return super.visitInputRef(inputRef);
        }
    }

    /**
     * 自动注册,需要类型转换.
     *
     * @param dbType 数据库中的字段类型对应的Java类型
     * @return 返回对应的Java类
     */
    public static Class<?> getJavaType(String dbType) {
        if (dbType == null) {
            return String.class; // Default
        }
        dbType = dbType.toUpperCase();
        switch (dbType) {
            case "VARCHAR" :
            case "STRING" :
            case "TEXT" :
                return String.class;
            case "INT" :
            case "INTEGER" :
                return Integer.class;
            case "BIGINT" :
                return Long.class;
            case "BOOLEAN" :
                return Boolean.class;
            case "DOUBLE" :
                return Double.class;
            case "FLOAT" :
                return Float.class;
            case "DECIMAL" :
            case "BIGDECIMAL" :
                return java.math.BigDecimal.class;
            case "UUID" :
                return String.class;
            case "DATE" :
                return java.sql.Date.class;
            case "TIMESTAMP" :
                return java.sql.Timestamp.class;
            case "LIST" :
            case "ARRAY" :
                return List.class;
            default :
                return String.class;
        }
    }

    /**
     * 根据列信息创建 RelDataType.
     * 特殊处理 ARRAY/LIST 类型，避免使用 createJavaType 导致的类型问题
     *
     * @param typeFactory 类型工厂
     * @param columnInfo 列信息
     * @return RelDataType
     */
    private static RelDataType createRelDataType(RelDataTypeFactory typeFactory, DataSourceTableColumnDto columnInfo) {
        String columnType = columnInfo.getColumnType();
        String columnJavaType = columnInfo.getColumnJavaType();

        // 处理 ARRAY/LIST 类型
        if ("ARRAY".equalsIgnoreCase(columnType) || "LIST".equalsIgnoreCase(columnType)) {
            // 创建 ARRAY<VARCHAR> 类型
            RelDataType elementType = typeFactory.createSqlType(SqlTypeName.VARCHAR, 65535);
            RelDataType arrayType = typeFactory.createArrayType(elementType, -1);
            return typeFactory.createTypeWithNullability(arrayType, true);
        }

        // 其他类型使用原有逻辑
        return typeFactory.createJavaType(getJavaType(columnJavaType));
    }

    /**
     * 从窗口函数中提取 PARTITION BY 使用的表前缀.
     * 优先使用 PARTITION BY 中带表前缀的列来确定表别名.
     *
     * @param sql sql
     * @return PARTITION BY 中使用的表别名，如果没有则返回 null
     */
    private static String extractPartitionByAlias(String sql) {
        // 匹配 PARTITION BY 后面的列，优先找带表前缀的
        // 模式：PARTITION BY t1.col 或 PARTITION BY t1.col, t1.col2
        Pattern partitionPattern = Pattern.compile(
                "(?i)PARTITION\\s+BY\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = partitionPattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }

        return null;
    }

    /**
     * 修复窗口函数中的列名歧义问题.
     * 优化：过滤关键字，防止注入非法的 where. 前缀.
     */
    public static String fixWindowFunctionColumnPrefix(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String result = sql;
        Set<String> tableAliases = new HashSet<>();
        Set<String> subqueryAliases = new HashSet<>();

        // 提取所有合法的表别名
        extractAllAliases(result, tableAliases, subqueryAliases);

        if (tableAliases.isEmpty() && subqueryAliases.isEmpty()) {
            return result;
        }

        Set<String> referencedAliases = extractReferencedAliases(result);
        referencedAliases.removeAll(subqueryAliases);

        Set<String> validAliases = new HashSet<>(referencedAliases);
        validAliases.retainAll(tableAliases);

        if (validAliases.isEmpty()) {
            validAliases = new HashSet<>(tableAliases);
            validAliases.removeAll(subqueryAliases);
        }

        List<String> sortedAliases = new ArrayList<>(validAliases);
        sortedAliases.sort(Comparator.comparingInt(String::length));

        if (sortedAliases.isEmpty()) {
            return result;
        }

        String partitionByAlias = extractPartitionByAlias(result);

        Pattern windowPattern = Pattern.compile(
                "(?i)(ORDER\\s+BY|PARTITION\\s+BY)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = windowPattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String columnName = matcher.group(2);

            // 检查列名是否已经有表前缀
            if (columnName.contains(".")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            // 检查列名是否与别名冲突
            Set<String> allAliases = new HashSet<>(tableAliases);
            allAliases.addAll(subqueryAliases);
            if (allAliases.contains(columnName.toLowerCase())) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            // 确定使用的表前缀
            String defaultAlias;
            if (partitionByAlias != null) {
                defaultAlias = partitionByAlias;
            } else {
                defaultAlias = sortedAliases.get(0);
            }

            String replacement = prefix + " " + defaultAlias + "." + columnName;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 提取所有表别名和子查询别名.
     * 增加关键字检查，解决 where 被识别为别名的问题.
     */
    private static void extractAllAliases(String sql, Set<String> tableAliases, Set<String> subqueryAliases) {
        // 匹配 FROM 后的别名
        Pattern fromPattern = Pattern.compile(
                "(?i)FROM\\s+[\\w\\.]+\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        // 匹配 JOIN 后的别名
        Pattern joinPattern = Pattern.compile(
                "(?i)JOIN\\s+[\\w\\.]+\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        // 匹配子查询别名
        Pattern subqueryPattern = Pattern.compile(
                "(?i)FROM\\s*\\([^)]+\\)\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        Matcher subqueryMatcher = subqueryPattern.matcher(sql);
        while (subqueryMatcher.find()) {
            String alias = subqueryMatcher.group(1).toLowerCase();
            if (!SQL_RESERVED_KEYWORDS.contains(alias)) {
                subqueryAliases.add(alias);
            }
        }

        Matcher fromMatcher = fromPattern.matcher(sql);
        while (fromMatcher.find()) {
            String alias = fromMatcher.group(1).toLowerCase();
            // 过滤掉关键字和已识别的子查询别名
            if (!SQL_RESERVED_KEYWORDS.contains(alias) && !subqueryAliases.contains(alias)) {
                tableAliases.add(alias);
            }
        }

        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            String alias = joinMatcher.group(1).toLowerCase();
            if (!SQL_RESERVED_KEYWORDS.contains(alias) && !subqueryAliases.contains(alias)) {
                tableAliases.add(alias);
            }
        }
    }

    /**
     * 从 SELECT 列表中提取所有被引用过的表别名.
     *
     * @param sql sql
     * @return 被引用过的表别名集合
     */
    private static Set<String> extractReferencedAliases(String sql) {
        Set<String> referenced = new HashSet<>();

        // 匹配所有类似 t1.column, t3.column 的模式
        // 但排除通配符引用如 t11.*
        Pattern columnPattern = Pattern.compile(
                "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]+)",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = columnPattern.matcher(sql);
        while (matcher.find()) {
            referenced.add(matcher.group(1).toLowerCase());
        }

        return referenced;
    }

    /**
     * 从 SQL 中提取所有表别名.
     *
     * @param sql sql
     * @return 表别名集合
     */
    private static Set<String> extractTableAliases(String sql) {
        Set<String> aliases = new HashSet<>();

        // 匹配 FROM 子句后的表别名
        // 例如: FROM table1 t1, table2 t2
        // 或: FROM table1 AS t1
        Pattern fromPattern = Pattern.compile(
                "(?i)FROM\\s+[\\w\\.]+\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        // 匹配 JOIN 子句后的表别名
        // 例如: JOIN table1 t1 ON ...
        // 或: JOIN table1 AS t1 ON ...
        Pattern joinPattern = Pattern.compile(
                "(?i)JOIN\\s+[\\w\\.]+\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        // 匹配子查询别名，例如: FROM (SELECT ...) t11
        Pattern subqueryPattern = Pattern.compile(
                "(?i)FROM\\s*\\([^)]+\\)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);

        // 匹配所有类似 t1.column, t3.column 的模式来提取表别名
        // 这是一个补充方法，可以捕获子查询中使用的别名
        Pattern columnPattern = Pattern.compile(
                "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.[a-zA-Z_][a-zA-Z0-9_]*",
                Pattern.CASE_INSENSITIVE);

        Matcher fromMatcher = fromPattern.matcher(sql);
        while (fromMatcher.find()) {
            aliases.add(fromMatcher.group(1).toLowerCase());
        }

        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            aliases.add(joinMatcher.group(1).toLowerCase());
        }

        Matcher subqueryMatcher = subqueryPattern.matcher(sql);
        while (subqueryMatcher.find()) {
            aliases.add(subqueryMatcher.group(1).toLowerCase());
        }

        // 从列引用中提取表别名
        Matcher columnMatcher = columnPattern.matcher(sql);
        while (columnMatcher.find()) {
            aliases.add(columnMatcher.group(1).toLowerCase());
        }

        return aliases;
    }

    /**
     * 替换数字开头的字段和SQL关键字.
     *
     * @param sql sql
     * @return 返回替换后的sql
     */
    public static String fixInvalidIdentifiers(String sql) {
        // 第一步：处理数字开头的标识符
        String result = fixNumericIdentifiers(sql);

        // 第二步：处理INTERVAL语法差异
        // PostgreSQL: interval '1 year' -> BigQuery: interval '1' YEAR
        result = fixIntervalSyntax(result);

        // 第三步：移除PostgreSQL特有的ON CONFLICT语法（Upsert）
        // ON CONFLICT对血缘解析不重要，但需要移除以避免解析错误
        result = removeOnConflictClause(result);

        // 第六步：修复JSON操作符 #>> 和 ->>（必须在CAST类型转换之前）
        result = fixJsonOperators(result);

        // 第七步：处理SQL关键字作为标识符（BigQuery需要用反引号包裹）
        result = escapeSqlKeywords(result);

        // 第四步：转换PostgreSQL特有的数据类型为标准SQL类型
        // int8 -> BIGINT, text -> VARCHAR
        result = fixPostgresqlDataTypes(result);
        // 第五步：转换PostgreSQL :: 类型转换运算符为标准SQL CAST语法
        // 例如：col::numeric -> CAST(col AS numeric), t2.col::varchar -> CAST(t2.col AS varchar)
        result = fixPostgresqlCastOperator(result);

        // 第八步：将 CONCAT 函数内的参数 CAST 成 VARCHAR
        // Calcite 的 CONCAT 只接受 VARCHAR 类型参数
        //result = fixConcatArgsToVarchar(result);

        // 第九步：修复窗口函数中的列名（ORDER BY/PARTITION BY 中没有表前缀的列）
        result = fixWindowFunctionColumnPrefix(result);

        return result;
    }

    /**
     * 将COALESCE替换为COALESCE_UDF.
     * Calcite内置的COALESCE在参数类型不兼容时（如String和TIMESTAMP）无法推断返回类型
    /**
     * 修复JSON操作符 #>> 和 ->>
     * PostgreSQL JSON 提取操作符不被 Calcite 支持
     *
     * @param sql sql
     * @return 返回修复后的sql
     */
    private static String fixJsonOperators(String sql) {
        String result = sql;

        // 第一步：处理 PostgreSQL :: json 语法
        // col:: json #>> '{path}' as alias -> CAST(col AS VARCHAR) AS alias
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s*::\\s+json\\s+#>>\\s*'\\{[^}]+\\}'\\s+as\\s+(\\w+),", "CAST($1 AS VARCHAR) AS $2,");
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s*::\\s+json\\s+#>>\\s*'\\{[^}]+\\}'\\s+as\\s+(\\w+)\\)", "CAST($1 AS VARCHAR) AS $2)");
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s*::\\s+json\\s+#>>\\s*'\\{[^}]+\\}'\\s+as\\s+(\\w+)$", "CAST($1 AS VARCHAR) AS $2");
        // col:: json -> CAST(col AS VARCHAR)
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s*::\\s+json\\b", "CAST($1 AS VARCHAR)");

        // 第二步：处理 CAST AS json/jsonb 语法
        // CAST(col AS json) -> CAST(col AS VARCHAR)
        result = result.replaceAll("(?i)CAST\\s*\\(([^)]+?)\\s+AS\\s+json\\)", "CAST($1 AS VARCHAR)");
        // CAST(col AS jsonb) -> CAST(col AS VARCHAR)
        result = result.replaceAll("(?i)CAST\\s*\\(([^)]+?)\\s+AS\\s+jsonb\\)", "CAST($1 AS VARCHAR)");
        // AS json -> AS VARCHAR
        result = result.replaceAll("(?i)\\s+AS\\s+json\\b", " AS VARCHAR");
        // AS jsonb -> AS VARCHAR
        result = result.replaceAll("(?i)\\s+AS\\s+jsonb\\b", " AS VARCHAR");

        // 第三步：处理 #>> 操作符，将路径提取替换为列引用
        // 模式: col #>> '{path}' AS alias -> col AS alias
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s+#>>\\s*'\\{[^}]+\\}'\\s+AS\\s+(\\w+),", "$1 AS $2,");
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s+#>>\\s*'\\{[^}]+\\}'\\s+AS\\s+(\\w+)\\)", "$1 AS $2)");
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s+#>>\\s*'\\{[^}]+\\}'\\s+AS\\s+(\\w+)$", "$1 AS $2");
        // 模式: col #>> '{path}') -> col)
        result = result.replaceAll("(?i)(\\w+\\.\\w+)\\s+#>>\\s*'\\{[^}]+\\}'\\)", "$1)");
        // 清理：移除孤立的 #>> 表达式
        result = result.replaceAll("(?i)\\s+#>>\\s*'\\{[^}]+\\}'", "");

        // 第四步：处理 ->> 操作符（JSON 文本提取）
        // 由于 jsonb 已转换为 VARCHAR，->> 操作符和路径直接移除
        result = result.replaceAll("(?i)\\s+->>\\s+'[^']+'", "");

        // 第五步：修复反引号嵌套问题
        result = result.replaceAll("(?i)CAST\\s*\\(\\s*`([^`]+)`\\s*\\.\\s*`([^`]+)`\\s+AS\\s+", "CAST($1.$2 AS ");

        return result;
    }

    /**
     * 解析IF函数的参数列表.
     * 正确处理嵌套括号中的逗号.
     *
     * @param argsStr 参数字符串，如 "a > 0, b, c"
     * @return 参数列表
     */
    private static List<String> parseIfArguments(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int lastPos = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(argsStr.substring(lastPos, i).trim());
                lastPos = i + 1;
            }
        }
        // 添加最后一个参数
        if (lastPos < argsStr.length()) {
            args.add(argsStr.substring(lastPos).trim());
        }
        return args;
    }

    /**
     * 将COALESCE函数转换为CASE WHEN表达式.
     * 解决 Calcite 无法推断 COALESCE 返回类型的问题，特别是当参数类型不兼容时
    /**
     * 处理INTERVAL语法差异.
     * 将 PostgreSQL 风格的 interval 'X unit' 转换为 BigQuery 风格的 interval 'X' UNIT
     * 例如：interval '1 year' -> interval '1' YEAR
     *
     * @param sql sql
     * @return 返回转换后的sql
     */
    private static String fixIntervalSyntax(String sql) {
        // 匹配 interval '数字 单位' 或 interval '数字单位' 格式
        // 支持：year, month, day, hour, minute, second, week, quarter 及其复数形式
        Pattern pattern = Pattern.compile(
                "interval\\s+'(\\d+)\\s*(year|years|month|months|day|days|hour|hours|minute|minutes|second|seconds|week|weeks|quarter|quarters)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2).toUpperCase();
            // 去掉复数形式的 S
            if (unit.endsWith("S")) {
                unit = unit.substring(0, unit.length() - 1);
            }
            matcher.appendReplacement(sb, "interval '" + number + "' " + unit);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 转换PostgreSQL特有的数据类型为标准SQL类型.
     * 例如：CAST(x AS int8) -> CAST(x AS BIGINT)
     *
     * @param sql sql
     * @return 返回转换后的sql
     */
    private static String fixPostgresqlDataTypes(String sql) {
        // 替换 PostgreSQL 特有的类型
        String result = sql;

        // 先处理已经被转义的 DATE（escapeSqlKeywords 会把 date 转义为 `date`）
        // t1.action_date::DATE -> t1.action_date::`date` -> t1.action_date::TIMESTAMP
        result = result.replaceAll("(?i)::`date`", "::TIMESTAMP");

        // int8 -> BIGINT (8字节整数)
        result = result.replaceAll("(?i)\\bint8\\b", "BIGINT");

        // int4 -> INTEGER
        result = result.replaceAll("(?i)\\bint4\\b", "INTEGER");

        // int2 -> SMALLINT
        result = result.replaceAll("(?i)\\bint2\\b", "SMALLINT");

        // timestamptz -> TIMESTAMP (PostgreSQL 时间戳类型)
        result = result.replaceAll("(?i)\\btimestamptz\\b", "TIMESTAMP");

        // jsonb -> VARCHAR (PostgreSQL JSONB 类型)
        result = result.replaceAll("(?i)\\bjsonb\\b", "VARCHAR");

        // float8 -> DOUBLE
        result = result.replaceAll("(?i)\\bfloat8\\b", "DOUBLE");

        // float4 -> REAL
        result = result.replaceAll("(?i)\\bfloat4\\b", "REAL");

        // text -> VARCHAR (或者保留为VARCHAR，Calcite支持)
        result = result.replaceAll("(?i)\\btext\\b", "VARCHAR");

        // bpchar -> CHAR
        result = result.replaceAll("(?i)\\bbpchar\\b", "CHAR");

        // STRING -> VARCHAR (DataFusion/Hive 类型)
        result = result.replaceAll("(?i)\\bSTRING\\b", "VARCHAR");

        // 特别处理 CAST(... AS STRING) 格式
        result = result.replaceAll("(?i)AS\\s+STRING\\b", "AS VARCHAR");

        // DATE -> TIMESTAMP (Calcite SqlBabel 支持 TIMESTAMP 类型)
        // 将 CAST(expr AS `date`) 或 CAST(expr AS date) 替换为 CAST(expr AS TIMESTAMP)
        // 处理被转义的 date：`date`
        result = result.replaceAll("(?i)CAST\\(([^)]+)\\s+AS\\s+`date`\\)", "CAST($1 AS TIMESTAMP)");
        // 处理未转义的 date
        result = result.replaceAll("(?i)CAST\\(([^)]+)\\s+AS\\s+date\\b(?!\\w)", "CAST($1 AS TIMESTAMP");

        // 注意：不要移除 TIMESTAMP 的转换，因为 TIMESTAMP + INTERVAL 是支持的
        // 如果需要强制类型转换，Calcite 会自动处理
        // 不再将 AS TIMESTAMP 替换为 AS VARCHAR

        // DATE 类型名单独出现时也转换（用于非CAST场景）
        // 注释掉：这会错误匹配复杂的 CAST 表达式
        // result = result.replaceAll("(?i)\\bCAST\\([^)]*\\bDATE\\b[^)]*\\)", "CAST(NULL AS VARCHAR)");

        // date_format -> DATE_FORMAT (标准SQL函数名)
        result = result.replaceAll("(?i)\\bdate_format\\s*\\(", "DATE_FORMAT(");

        // LAST_DAY -> last_day (使用自定义函数，避免Calcite内置的类型检查)
        result = result.replaceAll("(?i)\\bLAST_DAY\\s*\\(", "last_day(");

        // 将 concat_ws(',', collect_set(...)) 替换为 STRING_AGG(DISTINCT ..., ',')
        // 必须在 CONCAT_WS 和 COLLECT_SET 转换为小写之前执行
        result = replaceCollectSetWithStringAgg(result);

        // COLLECT_SET -> collect_set (使用自定义聚合函数)
        result = result.replaceAll("(?i)\\bCOLLECT_SET\\s*\\(", "collect_set(");

        // COLLECT_LIST -> collect_list (使用自定义聚合函数)
        result = result.replaceAll("(?i)\\bCOLLECT_LIST\\s*\\(", "collect_list(");

        // CONCAT_WS -> concat_ws (使用自定义函数)
        result = result.replaceAll("(?i)\\bCONCAT_WS\\s*\\(", "concat_ws(");

        // 简化剩余的 concat_ws 与 collect_set/collect_list 的嵌套
        // 由于 collect_set/collect_list 返回 ARRAY<OTHER>，concat_ws 无法正确处理这种类型
        // concat_ws(separator, collect_set(col)) -> collect_set(col)
        result = replaceNestedConcatWsFunction(result, "collect_set");

        // concat_ws(separator, collect_list(col)) -> collect_list(col)
        result = replaceNestedConcatWsFunction(result, "collect_list");

        //        // 移除 LATERAL VIEW 语法（Calcite 不支持）
        //        // 处理 LATERAL VIEW EXPLODE(t1.rely_node_arry) tmp AS rely_node_id
        //        // 对于血缘追踪，将 tmp.rely_node_id 替换为一个常量（类型不匹配不影响血缘）
        //        java.util.regex.Pattern lateralViewPattern = java.util.regex.Pattern.compile(
        //                "(?i)LATERAL\\s+VIEW\\s+EXPLODE\\s*\\(\\s*(\\w+\\.\\w+)\\s*\\)\\s+(\\w+)\\s+AS\\s+\\w+");
        //        java.util.regex.Matcher matcher = lateralViewPattern.matcher(result);
        //        java.util.Map<String, String> aliasMap = new java.util.HashMap<>();
        //        while (matcher.find()) {
        //            String originalCol = matcher.group(1); // t1.rely_node_arry
        //            String lateralAlias = matcher.group(2); // tmp
        //            aliasMap.put(lateralAlias, originalCol);
        //        }
        //        // 先替换所有 alias.column 为一个占位常量（避免类型不匹配问题）
        //        for (java.util.Map.Entry<String, String> entry : aliasMap.entrySet()) {
        //            // 将 alias.column 替换为 NULL（类型不影响血缘追踪）
        //            result = result.replaceAll("(?i)\\b" + entry.getKey() + "\\.\\w+\\b", "NULL");
        //        }
        //        // 再移除 LATERAL VIEW EXPLODE 部分
        //        java.util.regex.Pattern removePattern = java.util.regex.Pattern.compile(
        //                "(?i)\\s+LATERAL\\s+VIEW\\s+EXPLODE\\s*\\([^)]+\\)\\s+\\w+\\s+AS\\s+\\w+\\s*");
        //        result = removePattern.matcher(result).replaceAll(" ");
        //
        //        // 处理 LATERAL VIEW OUTER EXPLODE(col) alias AS column_alias
        //        result = result.replaceAll(
        //                "(?i)\\s+LATERAL\\s+VIEW\\s+OUTER\\s+EXPLODE\\s*\\([^)]+\\)\\s+\\w+\\s+AS\\s+\\w+\\s*",
        //                " ");
        //
        //        // 移除孤立的 LATERAL VIEW
        //        result = result.replaceAll("(?i)\\s+LATERAL\\s+VIEW\\s+\\w+\\s+AS\\s+\\w+\\s*", " ");
        //        result = result.replaceAll("(?i)\\s+LATERAL\\s+VIEW\\s+", " ");

        // 处理窗口函数的 RANGE BETWEEN 语法（BigQuery/Spark 语法）
        // RANGE BETWEEN ... PRECEDING AND ... FOLLOWING 在 Calcite 中可能导致类型不匹配
        // 将 RANGE 改为 ROWS
        result = result.replaceAll("(?i)RANGE\\s+BETWEEN", "ROWS BETWEEN");

        // SPLIT 函数 -> split (使用自定义函数)
        result = result.replaceAll("(?i)\\bSPLIT\\s*\\(", "split(");

        // LEFT ANTI JOIN -> LEFT JOIN (Calcite 不支持 ANTI JOIN)
        result = result.replaceAll("(?i)\\bLEFT\\s+ANTI\\s+JOIN\\b", "LEFT JOIN");
        // RIGHT ANTI JOIN -> RIGHT JOIN
        result = result.replaceAll("(?i)\\bRIGHT\\s+ANTI\\s+JOIN\\b", "RIGHT JOIN");
        // FULL ANTI JOIN -> FULL JOIN
        result = result.replaceAll("(?i)\\bFULL\\s+ANTI\\s+JOIN\\b", "FULL JOIN");

        //        // 移除 EXPLODE 语法（Calcite 不支持）
        //        result = result.replaceAll("(?i)EXPLODE\\s*\\([^)]+\\s*\\)", "");
        //        // 移除 POSEXPLODE 语法
        //        result = result.replaceAll("(?i)POSEXPLODE\\s*\\([^)]+\\s*\\)", "");

        // json_object_agg -> JSON_OBJECT (PostgreSQL -> Calcite)
        result = result.replaceAll("(?i)\\bjson_object_agg\\s*\\(", "JSON_OBJECT(");

        // json_build_object -> JSON_OBJECT (PostgreSQL -> Calcite)
        result = result.replaceAll("(?i)\\bjson_build_object\\s*\\(", "JSON_OBJECT(");

        // current_date - 1 -> current_date - INTERVAL '1' DAY
        // Calcite 核心处理是将整数天数转换为 INTERVAL DAY 类型
        result = result.replaceAll("(?i)\\bcurrent_date\\s*-\\s*(\\d+)", "current_date - INTERVAL '$1' DAY");

        // current_timestamp - 1 -> current_timestamp - INTERVAL '1' DAY
        result = result.replaceAll("(?i)\\bcurrent_timestamp\\s*-\\s*(\\d+)", "current_timestamp - INTERVAL '$1' DAY");

        // current_time - 1 -> current_time - INTERVAL '1' DAY (虽然不常见，但统一处理)
        result = result.replaceAll("(?i)\\bcurrent_time\\s*-\\s*(\\d+)", "current_time - INTERVAL '$1' DAY");

        return result;
    }

    /**
     * 转换PostgreSQL :: 类型转换运算符为标准SQL CAST语法.
     * 例如：
     *   col::numeric -> CAST(col AS numeric)
     *   t2.col::varchar -> CAST(t2.col AS varchar)
     *   `t2`.`col`::varchar -> CAST(`t2`.`col` AS varchar)
     *   '123'::int -> CAST('123' AS int)
     *
     * @param sql sql
     * @return 返回转换后的sql
     */
    private static String fixPostgresqlCastOperator(String sql) {
        String result = sql;

        // 模式1: 列名或表名.列名::类型（支持反引号包裹的标识符）
        // 匹配: identifier::type, table.identifier::type, `table`.`col`::type 等
        // 支持类型名中带有括号，如 numeric(10,2)
        // identifierPart 匹配: word 或 `word`
        String identifierPart = "(?:`?\\w+`?)";
        Pattern pattern = Pattern.compile(
                "(" + identifierPart + "(?:\\." + identifierPart + ")?)\\s*::\\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\\s*\\([^\\)]*\\))?)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String targetType = matcher.group(2);
            matcher.appendReplacement(sb, "CAST(" + expression + " AS " + targetType + ")");
        }
        matcher.appendTail(sb);
        result = sb.toString();

        // 模式2: 字符串字面量::类型 (如 '123'::int)
        // 需要处理引号内的内容
        Pattern literalPattern = Pattern.compile(
                "'([^']*)'\\s*::\\s*([a-zA-Z_][a-zA-Z0-9_]*)",
                Pattern.CASE_INSENSITIVE);
        Matcher literalMatcher = literalPattern.matcher(result);
        StringBuffer literalSb = new StringBuffer();

        while (literalMatcher.find()) {
            String literal = literalMatcher.group(1);
            String targetType = literalMatcher.group(2);
            literalMatcher.appendReplacement(literalSb, "CAST('" + literal + "' AS " + targetType + ")");
        }
        literalMatcher.appendTail(literalSb);
        result = literalSb.toString();

        // 模式3: 函数调用::类型 (如 to_char(...):TIMESTAMP, some_func(...):type)
        // 处理函数调用后跟 :: 类型转换运算符的情况
        // 需要匹配括号平衡
        result = fixFunctionCastOperator(result);

        return result;
    }

    /**
     * 处理函数调用后的 :: 类型转换运算符.
     * 例如: to_char(current_date - INTERVAL '1' DAY, 'yyyy-mm-dd 23:59:59')::TIMESTAMP
     * 转换为: CAST(to_char(current_date - INTERVAL '1' DAY, 'yyyy-mm-dd 23:59:59') AS TIMESTAMP)
     *
     * @param sql sql
     * @return 转换后的sql
     */
    private static String fixFunctionCastOperator(String sql) {
        StringBuffer result = new StringBuffer();
        int i = 0;
        while (i < sql.length()) {
            // 查找 :: 运算符
            if (i + 1 < sql.length() && sql.charAt(i) == ':' && sql.charAt(i + 1) == ':') {
                // 找到 :: ，向前找到函数调用的开始
                int funcStart = findFunctionStart(sql, i);
                if (funcStart > 0) {
                    // 找到函数开始，向后找到函数的结束括号
                    int funcEnd = findMatchingParen(sql, funcStart);
                    if (funcEnd > funcStart) {
                        // 提取函数调用和类型
                        String funcCall = sql.substring(funcStart, funcEnd + 1);
                        // 跳过 :: 和空格
                        int typeStart = i + 2;
                        while (typeStart < sql.length() && Character.isWhitespace(sql.charAt(typeStart))) {
                            typeStart++;
                        }
                        // 提取类型名
                        int typeEnd = typeStart;
                        while (typeEnd < sql.length() && (Character.isLetterOrDigit(sql.charAt(typeEnd)) || sql.charAt(typeEnd) == '_')) {
                            typeEnd++;
                        }
                        String targetType = sql.substring(typeStart, typeEnd);

                        // 替换为 CAST(funcCall AS type)
                        result.append("CAST(").append(funcCall).append(" AS ").append(targetType).append(")");
                        i = typeEnd;
                        continue;
                    }
                }
            }
            result.append(sql.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * 从指定位置向前找到函数调用的开始.
     *
     * @param sql SQL语句
     * @param pos 当前位置（指向 :: 的前一个字符）
     * @return 函数调用的开始位置
     */
    private static int findFunctionStart(String sql, int pos) {
        // 跳过空格
        int i = pos - 1;
        while (i >= 0 && Character.isWhitespace(sql.charAt(i))) {
            i--;
        }
        if (i < 0)
            return -1;

        // 如果当前是 )，找到匹配的 (
        if (sql.charAt(i) == ')') {
            int depth = 1;
            i--;
            while (i >= 0 && depth > 0) {
                if (sql.charAt(i) == ')')
                    depth++;
                else if (sql.charAt(i) == '(')
                    depth--;
                i--;
            }
            // 现在 i 指向 ( 的前一个字符
            // 继续向前找到函数名
            while (i >= 0 && Character.isWhitespace(sql.charAt(i))) {
                i--;
            }
            if (i >= 0) {
                // 找到函数名的开始
                int nameEnd = i + 1;
                while (i >= 0 && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i--;
                }
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 移除PostgreSQL特有的ON CONFLICT语法（Upsert）.
     * 例如：INSERT INTO ... ON CONFLICT (key) DO UPDATE SET ...
     * 转换为：INSERT INTO ...（截断ON CONFLICT部分）
     *
     * @param sql sql
     * @return 返回移除ON CONFLICT后的sql
     */
    private static String removeOnConflictClause(String sql) {
        // 匹配 ON CONFLICT ... 直到语句结束
        // 使用多行模式，忽略大小写
        Pattern pattern = Pattern.compile(
                "\\s+ON\\s+CONFLICT\\b.*",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        return matcher.replaceFirst("");
    }

    /**
     * 替换嵌套的 concat_ws(separator, collect_set/collect_list(...)) 结构.
     * 例如: concat_ws(',', collect_set(col)) -> collect_set(col)
     *       concat_ws(',', collect_set(CAST(col AS STRING))) -> collect_set(CAST(col AS STRING))
     *
     * @param sql          原始SQL
     * @param nestedFunc   内层函数名 (collect_set 或 collect_list)
     * @return 替换后的SQL
     */
    private static String replaceNestedConcatWsFunction(String sql, String nestedFunc) {
        // 模式: concat_ws(separator, nestedFunc(...)) 替换为 nestedFunc(...)
        String pattern = "(?i)concat_ws\\s*\\(\\s*[^,]+,\\s*" + nestedFunc + "\\s*\\(";

        // 使用 Pattern 和 Matcher 来找到匹配位置
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (m.find()) {
            int start = m.start();
            int concatWsEnd = findMatchingParen(sql, m.end() - 1);

            if (concatWsEnd > m.end()) {
                // 找到了 concat_ws 的完整括号范围
                // 现在在这个范围内找到 collect_set/collect_list 的参数
                String nestedPattern = "(?i)" + nestedFunc + "\\s*\\(";
                Pattern nestedP = Pattern.compile(nestedPattern);
                Matcher nestedM = nestedP.matcher(sql);

                // 在 concat_ws 括号范围内找到 nestedFunc 的开始位置
                if (nestedM.find(m.end())) {
                    int nestedStart = nestedM.start();
                    int nestedEnd = findMatchingParen(sql, nestedM.end() - 1);

                    if (nestedEnd > nestedStart) {
                        // 提取 nestedFunc 括号内的内容
                        String nestedContent = sql.substring(nestedM.end(), nestedEnd);
                        // 替换为 nestedFunc(content)，使用 quoteReplacement 处理特殊字符
                        String replacement = nestedFunc + "(" + nestedContent + ")";
                        m.appendReplacement(result, Matcher.quoteReplacement(replacement));
                        continue;
                    }
                }
            }
            // 如果没有找到匹配，保留原样
            m.appendReplacement(result, Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(result);

        return result.toString();
    }

    /**
     * 将 concat_ws(',', collect_set(...)) 替换为 STRING_AGG(DISTINCT ..., ',').
     * STRING_AGG 直接返回 VARCHAR，更符合语义.
     * 例如：concat_ws(',', collect_set(price_sub_category_name)) -> STRING_AGG(DISTINCT price_sub_category_name, ',')
     *       concat_ws(separator, collect_set(col)) -> STRING_AGG(DISTINCT col, separator)
     * @param sql 原始SQL
     * @return 替换后的SQL
     */
    private static String replaceCollectSetWithStringAgg(String sql) {
        String result = sql;

        // 查找 concat_ws(sep, collect_set(...)) 模式
        // 匹配：concat_ws('分隔符', collect_set(内容)) 或 CONCAT_WS('分隔符', COLLECT_SET(内容))
        // 使用不区分大小写的匹配
        Pattern pattern = Pattern.compile(
                "(?i)(CONCAT_WS|concat_ws)\\s*\\(\\s*'([^']+)'(?:\\s*,\\s*|\\s+)(COLLECT_SET|collect_set)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String separator = matcher.group(2); // 分隔符，如 ','

            // 找到 concat_ws 的完整括号范围
            int concatWsStart = matcher.start(1); // CONCAT_WS/concat_ws 的开始位置
            int afterCollectSet = matcher.end(3); // COLLECT_SET/collect_set 的 ")" 位置之后

            // 找到与 collect_set 的 '(' 匹配的 ')'
            // 定位到 collect_set 的 '(' 位置
            String fullMatch = matcher.group();
            int collectSetParenStart = matcher.start(3) + matcher.group(3).length(); // 跳过 "COLLECT_SET(" 或 "collect_set("
            int collectSetEnd = findMatchingParen(result, collectSetParenStart);

            if (collectSetEnd > collectSetParenStart) {
                // 提取 collect_set 括号内的内容
                String content = result.substring(collectSetParenStart + 1, collectSetEnd);

                // 找到 concat_ws 的完整闭合括号
                int concatWsEnd = findMatchingParen(result, concatWsStart);

                if (concatWsEnd > concatWsStart) {
                    // 替换为 STRING_AGG(DISTINCT content, separator)
                    String replacement = "STRING_AGG(DISTINCT " + content + ", '" + separator + "')";
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    /**
     * 找到与指定位置括号匹配的闭合括号位置.
     *
     * @param sql    SQL语句
     * @param openParenPos  '(' 字符的位置
     * @return 对应的 ')' 字符的位置，如果找不到返回 -1
     */
    private static int findMatchingParen(String sql, int openParenPos) {
        if (openParenPos < 0 || openParenPos >= sql.length() || sql.charAt(openParenPos) != '(') {
            return -1;
        }

        int depth = 1;
        for (int i = openParenPos + 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 修复 TIMESTAMP + INTERVAL 类型不匹配问题.
     *
     * 处理以下情况：
     * 1. CAST(t1.col AS TIMESTAMP) + INTERVAL '120' MINUTE
     * 2. t1.timestamp_col + INTERVAL '120' MINUTE (列引用)
     * 由于 Calcite 对 CAST 后的类型推断可能有问题，需要确保类型正确
     * 解决方案：在 CAST 表达式外层再包裹一层 CAST
     *
     * @param sql 原始SQL
     * @return 修复后的SQL
     */
    @SuppressWarnings("checkstyle:LineLength")
    private static String fixTimestampIntervalOperation(String sql) {
        String result = sql;

        // 模式1: CAST(表达式 AS TIMESTAMP) + INTERVAL 'num' UNIT
        // 需要在外层再包裹 CAST
        // 例如：CAST(t1.sunset_time AS TIMESTAMP) + INTERVAL '120' MINUTE
        // 变为：CAST(CAST(t1.sunset_time AS TIMESTAMP) AS TIMESTAMP) + INTERVAL '120' MINUTE
        // 直接查找完整模式并替换
        Pattern pattern1 = Pattern.compile(
                "(CAST\\s*\\(\\s*[^()]+\\s+AS\\s+TIMESTAMP\\s*\\))\\s*\\+\\s+INTERVAL\\s+'\\d+'\\s+(?:MINUTE|HOUR|DAY|MONTH|YEAR|MINUTES|HOURS|DAYS|MONTHS|YEARS)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(result);
        StringBuffer sb1 = new StringBuffer();
        while (matcher1.find()) {
            String castExpr = matcher1.group(1);
            // intervalExpr 已经包含 "+ INTERVAL 'num' UNIT"，直接拼接
            String intervalExpr = matcher1.group(0).substring(castExpr.length());
            // 外层再包裹 CAST：CAST(castExpr AS TIMESTAMP) intervalExpr（注意中间没有+）
            String replacement = "CAST(" + castExpr + " AS TIMESTAMP) " + intervalExpr;
            matcher1.appendReplacement(sb1, Matcher.quoteReplacement(replacement));
        }
        matcher1.appendTail(sb1);
        result = sb1.toString();

        // 模式2: 列引用 + INTERVAL 'num' UNIT
        // 例如：t1.sunset_time + INTERVAL '120' MINUTE
        // 需要添加外层 CAST
        // 注意：这可能过于激进，只处理明确的时间戳列名模式
        // 匹配如：t1.sunset_time + INTERVAL、t1.action_time + INTERVAL 等
        Pattern pattern2 = Pattern.compile(
                "(\\w+\\.\\w+)\\s*\\+\\s+INTERVAL\\s+'\\d+'\\s+(?:MINUTE|HOUR|DAY|MONTH|YEAR|MINUTES|HOURS|DAYS|MONTHS|YEARS)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(result);
        StringBuffer sb2 = new StringBuffer();
        while (matcher2.find()) {
            String colRef = matcher2.group(1);
            // 添加外层 CAST
            String replacement = "CAST(" + colRef + " AS TIMESTAMP)";
            matcher2.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
        }
        matcher2.appendTail(sb2);
        result = sb2.toString();

        return result;
    }

    /**
     * 处理数字开头的标识符.
     * 修复：精确匹配数字开头的标识符，避免过度匹配导致反引号位置错乱
     * 例如：t1.5abc -> t1.`5abc`，但 t1.5 不应被处理（可能是数值）
     */
    private static String fixNumericIdentifiers(String sql) {
        // 模式：alias.数字开头且包含字母的标识符
        // 必须以字母结尾（如 5abc），确保精确匹配
        // 不处理纯数字结尾（如 t1.5 或 t1.5.2），这些是数值表达式
        Pattern pattern = Pattern.compile("(\\b\\w+)\\.(\\d+[a-zA-Z_]\\w*)");
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String invalidIdentifier = matcher.group(2);
            matcher.appendReplacement(sb, alias + ".`" + invalidIdentifier + "`");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * SQL关键字列表（作为标识符时需要转义）.
     * 注意：TIMESTAMP 是有效的 SQL 类型关键字，不要转义，否则 CAST(... AS `TIMESTAMP`) 会报错
     */
    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "time", "date"));

    /**
     * 转义SQL关键字.
     * 对于BigQuery Lex，关键字作为标识符需要用反引号包裹.
     */
    private static String escapeSqlKeywords(String sql) {
        String result = sql;

        for (String keyword : SQL_KEYWORDS) {
            // 情况1: 匹配 alias.keyword （带别名前缀）
            String patternWithAlias = "(\\b\\w+)\\.\\b(" + keyword + ")\\b(?![a-zA-Z0-9_])";
            result = escapeKeywordWithPattern(result, patternWithAlias, true, keyword);

            // 情况2: 匹配单独的 keyword （不带别名前缀，作为列名）
            // 使用负向回顾：前面不能是点号（避免匹配 alias.time 中的 time 部分重复处理）
            // 使用负向先行：后面不能是点号（避免匹配 time.xxx）
            String patternWithoutAlias = "(?<![a-zA-Z0-9_.])\\b(" + keyword + ")\\b(?![a-zA-Z0-9_.])";
            result = escapeKeywordWithPattern(result, patternWithoutAlias, false, keyword);
        }

        return result;
    }

    /**
     * 根据模式转义关键字.
     *
     * @param sql 原始SQL
     * @param patternStr 正则模式
     * @param hasAlias 是否包含别名前缀
     * @param keyword 关键字
     * @return 处理后的SQL
     */
    private static String escapeKeywordWithPattern(String sql, String patternStr, boolean hasAlias, String keyword) {
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String matchedKeyword = matcher.group(hasAlias ? 2 : 1);
            int keywordStart = matcher.start(hasAlias ? 2 : 1);

            // 检查是否已经被反引号包裹
            if (keywordStart > 0 && sql.charAt(keywordStart - 1) == '`') {
                matcher.appendReplacement(sb, matcher.group());
            } else {
                if (hasAlias) {
                    String alias = matcher.group(1);
                    matcher.appendReplacement(sb, alias + ".`" + matchedKeyword + "`");
                } else {
                    matcher.appendReplacement(sb, "`" + matchedKeyword + "`");
                }
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 自动注册,注册表实现类.
     */
    private static class SimpleTable extends AbstractTable {

        /**
         * 数据类型.
         */
        private final RelDataType rowType;

        /**
         * 构造函数.
         */
        SimpleTable(RelDataType rowType) {
            this.rowType = rowType;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return rowType;
        }
    }

    /**
     * 将 CONCAT 函数内的参数 CAST 成 VARCHAR.
     * Calcite 的 CONCAT 只接受 VARCHAR 类型参数
     *
     * @param sql 原始 SQL
     * @return 修复后的 SQL
     */
    private static String fixConcatArgsToVarchar(String sql) {
        // 使用正则表达式匹配 CONCAT(...) 块
        // 策略：找到 CONCAT(，然后找到匹配的 )，但要正确处理嵌套括号

        return fixConcatRecursive(sql, 0);
    }

    /**
     * 递归处理 CONCAT，将参数 CAST 成 VARCHAR.
     * 使用深度检测防止无限递归
     */
    private static String fixConcatRecursive(String sql, int depth) {
        if (depth > 10) {
            return sql; // 防止无限递归
        }

        StringBuffer sb = new StringBuffer();
        int i = 0;
        int sqlLen = sql.length();

        while (i < sqlLen) {
            // 查找 CONCAT(
            if (i + 7 <= sqlLen && sql.substring(i, i + 7).equalsIgnoreCase("CONCAT(")) {
                // 找到 CONCAT(，查找匹配的 )
                int concatStart = i;
                int parenStart = i + 7; // CONCAT( 之后的位置
                int parenDepth = 1;
                int j = parenStart;

                while (j < sqlLen && parenDepth > 0) {
                    if (sql.charAt(j) == '(') {
                        parenDepth++;
                    } else if (sql.charAt(j) == ')') {
                        parenDepth--;
                    }
                    j++;
                }

                if (parenDepth == 0) {
                    // 找到完整的 CONCAT(...)
                    String innerArgs = sql.substring(parenStart, j - 1);

                    // 递归处理内部的 CONCAT
                    String processedInner = fixConcatRecursive(innerArgs, depth + 1);

                    // 将内部参数的 CAST 应用
                    String processedArgs = castConcatArgs(processedInner);

                    sb.append("CONCAT(");
                    sb.append(processedArgs);
                    sb.append(")");

                    i = j;
                    continue;
                }
            }

            sb.append(sql.charAt(i));
            i++;
        }

        return sb.toString();
    }

    /**
     * 将 CONCAT 的参数列表中的每个参数 CAST 成 VARCHAR.
     * 正确处理嵌套的括号
     */
    private static String castConcatArgs(String args) {
        List<String> argsList = new ArrayList<>();
        int depth = 0;
        int lastComma = 0;

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                // 找到一个参数的边界
                String arg = args.substring(lastComma, i).trim();
                argsList.add(castSingleArg(arg));
                lastComma = i + 1;
            }
        }
        // 最后一个参数
        if (lastComma < args.length()) {
            String arg = args.substring(lastComma).trim();
            argsList.add(castSingleArg(arg));
        }

        return String.join(", ", argsList);
    }

    /**
     * 将单个参数 CAST 成 VARCHAR（如果不是 CAST 的话）.
     */
    private static String castSingleArg(String arg) {
        String trimmed = arg.trim();
        // 如果已经是 CAST，不处理
        if (trimmed.toUpperCase().startsWith("CAST(")) {
            return trimmed;
        }
        // 其他情况都 CAST 成 VARCHAR
        return "CAST(" + trimmed + " AS VARCHAR)";
    }

    /**
     * 解析 CONCAT 函数的参数列表.
     * 正确处理嵌套括号中的逗号
     *
     * @param argsStr 参数字符串
     * @return 参数列表
     */
    private static List<String> parseConcatArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int lastPos = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(argsStr.substring(lastPos, i).trim());
                lastPos = i + 1;
            }
        }
        if (lastPos < argsStr.length()) {
            args.add(argsStr.substring(lastPos).trim());
        }
        return args;
    }
}

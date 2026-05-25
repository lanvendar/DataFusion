package com.datafusion.manager.metadata.support;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.odps.Column;
import com.aliyun.odps.Instance;
import com.aliyun.odps.Odps;
import com.aliyun.odps.OdpsException;
import com.aliyun.odps.Partition;
import com.aliyun.odps.Table;
import com.aliyun.odps.account.Account;
import com.aliyun.odps.account.AliyunAccount;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.task.SQLTask;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.ConnectTypeEnum;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.common.type.TypeInfoParser;
import com.datafusion.common.utils.MathUtil;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareInfo;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareResultDto;
import com.datafusion.manager.metadata.enums.TableColumnCompareEnum;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.MaxcomputeTableColumn;
import com.datafusion.manager.metadata.support.model.MaxcomputeTableCreateParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.MaxcomputeMapper;
import com.datafusion.manager.utils.AesUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static com.datafusion.common.constant.SystemConstant.LINE_FEED;

/**
 * maxcompute数据库服务
 * 
 * @author xufeng
 * @version 1.0.0, 2026/2/5
 * @since 2026/2/5
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class MaxcomputeSupport extends AbstractJdbcSupport<List<MaxcomputeTableColumn>> {

    /**
     * maxcompute数据眼Mapper.
     */
    private final MaxcomputeMapper mapper;

    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder;

    @Override
    protected String getDefaultDriverClass() {
        return DatabaseTypeEnum.MaxcomputeDriver.DEFAULT.getDriverClassName();
    }

    @Override
    protected String generateJdbcUrl(DataSourceInfo info) {
        String address = String.format("%s%s", "jdbc:odps:https://", info.getHost());
        String jdbcUrl = String.format("%s?project=%s&%s&%s", address, info.getDatabaseName(),
            parseExtendParam(info.getExtendParam()), "settings={\"odps.namespace.schema\":\"true\"}");

        if (jdbcUrl.endsWith(SystemConstant.QUESTION_MARK)) {
            jdbcUrl = jdbcUrl.substring(0, jdbcUrl.length() - 1);
        }
        return jdbcUrl;
    }

    @Override
    protected List<MaxcomputeTableColumn> queryMetaData(DataSourceInfo dsInfo, List<String> tableNames) {
        return mapper.getMetaData(dsInfo, dsInfo.getSchemaName(), tableNames);
    }

    private List<MaxcomputeTableColumn> transformDto(List<MaxcomputeTableColumn> list) {
        if (CollectionUtil.isEmpty(list)) {
            return list;
        }
        list.forEach(m -> {
            TypeInfo parse = TypeInfoManager.parse(DatabaseTypeEnum.MAXCOMPUTE, m.getDataType().toUpperCase());
            m.setColumnType(getColumnType(parse));
            m.setFullColumnType(m.getFullColumnType());
            m.setColumnLength(parse.getLength());
            m.setColumnPrecision(parse.getPrecision());
            m.setScale(parse.getScale());
        });
        return list;
    }

    /**
     * getColumnType.
     *
     * @param typeInfo
     *            getColumnType
     * @return String
     */
    private String getColumnType(com.datafusion.common.type.TypeInfo typeInfo) {
        if ("ARRAY".equals(typeInfo.getFieldType()) || "MAP".equals(typeInfo.getFieldType())
            || "STRUCT".equals(typeInfo.getFieldType())) {
            return typeInfo.getFullFieldType();
        }
        return typeInfo.getFieldType();
    }

    private String getPartitionKeyWithTypes(List<MaxcomputeTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return "";
        }
        return columns.stream().filter(col -> Boolean.TRUE.equals(col.getIsPartitionKey()))
            .sorted(Comparator.comparing(MaxcomputeTableColumn::getOrdinalPosition)).map(col -> col.getColumnName())
            .collect(Collectors.joining(","));
    }

    private String getPrimaryKeyWithTypes(List<MaxcomputeTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return "";
        }
        return columns.stream().filter(col -> Boolean.TRUE.equals(col.getIsPrimary()))
            .sorted(Comparator.comparing(MaxcomputeTableColumn::getOrdinalPosition)).map(col -> col.getColumnName())
            .collect(Collectors.joining(","));
    }

    /**
     * 获取非分区字段中最大的序号 (ordinalPosition)
     */
    private Integer getMaxOrdinalPositionOfNonPartitionColumns(List<MaxcomputeTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return 0;
        }

        return columns.stream()
            // 1. 过滤掉分区字段 (isPartitionKey 为 null 或 false 的才是普通列)
            .filter(col -> !Boolean.TRUE.equals(col.getIsPartitionKey()))
            // 2. 提取序号，过滤掉序号为空的异常数据
            .map(MaxcomputeTableColumn::getOrdinalPosition).filter(Objects::nonNull)
            // 3. 取最大值
            .max(Comparator.naturalOrder())
            // 4. 如果没有非分区字段，返回 0
            .orElse(0);
    }

    @Override
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<MaxcomputeTableColumn> list) {

        list = transformDto(list);
        Map<String, List<MaxcomputeTableColumn>> sourceTableMap =
            list.stream().collect(Collectors.groupingBy(MaxcomputeTableColumn::getTableName));
        Map<String, TableInfo> tableMap = new HashMap<>();
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(ds.getDatabaseType()));
        for (Map.Entry<String, List<MaxcomputeTableColumn>> entry : sourceTableMap.entrySet()) {
            String tableName = entry.getKey();
            List<MaxcomputeTableColumn> columnList = entry.getValue();
            // 表
            TableInfo table = tableMap.computeIfAbsent(tableName, t -> new TableInfo());
            table.setSchemaId(ds.getId());
            table.setTableName(tableName);
            table.setTableDesc(columnList.get(0).getTableDesc());
            table.setIsView(columnList.get(0).getIsView());
            table.setViewDef(columnList.get(0).getViewDef());
            table.setIsModify(false);
            Properties props = new Properties();
            if (columnList.stream().anyMatch(m -> m.getIsPrimary())) {
                props.setProperty(TablePropertiesOptions.PRIMARY_KEYS.key(), getPrimaryKeyWithTypes(columnList));
            }
            if (columnList.stream().anyMatch(m -> m.getIsPartitionKey())) {
                props.setProperty(TablePropertiesOptions.PARTITION_KEYS.key(), getPartitionKeyWithTypes(columnList));
            }
            table.setTableProperties(props);
            Integer maxPosition = getMaxOrdinalPositionOfNonPartitionColumns(columnList);
            for (MaxcomputeTableColumn ptc : columnList) {
                List<TableColumnInfo> columns = columnMap.computeIfAbsent(tableName, col -> new ArrayList<>());
                TableColumnInfo column = new TableColumnInfo();
                columns.add(column);
                column.setTableName(tableName);
                column.setColumnName(ptc.getColumnName());
                column.setColumnDesc(ptc.getColumnDesc());
                column.setColumnSerial(ptc.getOrdinalPosition() + (ptc.getIsPartitionKey() ? maxPosition : 0));
                column.setColumnType(ptc.getColumnType());
                column.setColumnLength(ptc.getColumnLength());
                column.setColumnPrecision(ptc.getColumnPrecision());
                column.setScale(ptc.getScale());
                column.setIsNullable(ptc.getIsNullable());
                column.setFullColumnType(ptc.getFullColumnType());
                column.setIsPrimary(ptc.getIsPrimary());
                // 在建表等场景下,需要全字段
                if (StrUtil.isEmpty(ptc.getFullColumnType())) {
                    TypeInfo typeInfo = parser.parse(column.getColumnType(), column.getColumnLength(),
                        column.getColumnPrecision(), column.getScale());
                    column.setFullColumnType(typeInfo.getFullFieldType());
                } else {
                    column.setFullColumnType(ptc.getFullColumnType());
                }
            }
        }

        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        return metadata;
    }

    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.MAXCOMPUTE;
    }

    @Override
    public boolean tryConnect(DataSourceInfo info) {
        try {
            Integer connected = mapper.tryConnect(info);
            return 1 == connected;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        return 0L;
    }

    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        Odps odps = createOpdsClient(ds);
        String result = "O B";
        try {
            // 1. 获取 Table 对象
            Table table = odps.tables().get(tableName);

            // 2. 获取存储大小（元数据操作，速度快）
            // table.reload() 可以确保获取最新的元数据，包括大小
            table.reload();
            result = MathUtil.fileSizeConverter(table.getSize());

        } catch (Exception e) {
            log.error("Failed to get stats for table: " + tableName);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getDataPreview(DataSourceInfo ds, DataPreviewQuery condition) {
        condition.setQueryConditions(
                processPartitionConditions(ds, condition.getTableName(), condition.getQueryConditions()));
        return mapper.getDataPreview(ds, condition);
    }

    private Odps createOpdsClient(DataSourceInfo ds) {

        Account account = new AliyunAccount(ds.getUsername(), ds.getPassword());
        Odps odps = new Odps(account);
        if (ds.getExtendParam() != null && ds.getExtendParam().get("endpoint") != null) {
            odps.setEndpoint(ds.getExtendParam().get("endpoint").toString());
        }
        odps.setDefaultProject(ds.getDatabaseName());
        return odps;
    }

    /**
     * 核心逻辑：处理分区条件.
     *
     * @param ds
     *            ds 实例
     * @param tableName
     *            表名
     * @param userConditions
     *            用户输入的查询条件
     * @return 最终用于查询的条件列表
     * @throws OdpsException @
     */
    private List<String> processPartitionConditions(DataSourceInfo ds, String tableName, List<String> userConditions) {
        Odps odps = createOpdsClient(ds);
        Table table = odps.tables().get(tableName);
        List<Column> partitionColumns = table.getSchema().getPartitionColumns();

        // 如果不是分区表，直接返回用户条件
        if (partitionColumns.isEmpty()) {
            return userConditions;
        }

        // 检查用户条件是否已包含分区过滤
        Set<String> partitionColumnNames = partitionColumns.stream().map(Column::getName).collect(Collectors.toSet());
        boolean partitionConditionExists = userConditions != null && userConditions.stream().anyMatch(
            cond -> partitionColumnNames.stream().anyMatch(pCol -> cond.toLowerCase().contains(pCol.toLowerCase())));

        List<String> finalConditions = new ArrayList<>();
        if (userConditions != null) {
            finalConditions.addAll(userConditions);
        }

        // 要求2 & 3: 如果用户条件中没有包含分区信息，则自动添加
        if (!partitionConditionExists) {
            // 获取最新分区
            List<Partition> partitions = table.getPartitions();
            if (partitions.isEmpty()) {
                // 如果一个分区表没有任何分区，它实际上是空的，无法查询
                // 抛出异常或返回空结果，这里选择抛出明确的异常
                throw new IllegalStateException(
                    "Partitioned table '" + tableName + "' has no partitions, cannot query.");
            }
            // 通常最新的分区在列表末尾（按字典序）
            Partition latestPartition = partitions.get(partitions.size() - 1);
            String partitionSpec = latestPartition.getPartitionSpec().toString(); // e.g., "ds='20231231',pt='hangzhou'"
            String partitionFilter = partitionSpec.replace(",", " AND "); // "ds='20231231' AND pt='hangzhou'"

            log.info("Auto-adding partition filter for table '" + tableName + "': " + partitionFilter);
            finalConditions.add(partitionFilter);
        }

        return finalConditions;
    }

    @Override
    public String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        List<MaxcomputeTableCreateParam> createParams = buildCreateTableParam(metaData);
        StringBuilder sb = new StringBuilder();
        if (CollectionUtil.isNotEmpty(createParams)) {
            for (MaxcomputeTableCreateParam param : createParams) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("tableInfo", param.getTableInfo());
                paramMap.put("columnInfos", param.getColumnInfos());
                paramMap.put("partitionColumnInfos", param.getPartitionColumnInfos());
                String sql = builder.renderSql(ds.getDatabaseType() + ".createTable", paramMap).getSql();
                sb.append("--").append(param.getTableInfo().getTableName()).append("表结构创建").append(LINE_FEED);
                sb.append(sql).append(System.lineSeparator());
            }
        }
        return new String(sb);
    }

    /**
     * 通过元数据构建建表请求对象.
     *
     * @param metaData
     *            元数据
     * @return 建表请求List
     */
    private List<MaxcomputeTableCreateParam> buildCreateTableParam(MetaDataInfo metaData) {
        List<MaxcomputeTableCreateParam> params = new ArrayList<>();
        Map<String, List<TableColumnInfo>> columns = metaData.getColumns();
        metaData.getTables().forEach(table -> {
            MaxcomputeTableCreateParam request = new MaxcomputeTableCreateParam();
            request.setTableInfo(table);
            List<TableColumnInfo> normalColumns;
            List<TableColumnInfo> partitionColumns = new ArrayList<>();

            log.info("分区信息:" + table.getPartitionKeys());
            List<TableColumnInfo> tableColumns = columns.getOrDefault(table.getTableName(), new ArrayList<>());
            if (StrUtil.isNotBlank(table.getPartitionKeys())) {

                Set<String> userPartitionNames =
                    Arrays.stream(table.getPartitionKeys().split(SystemConstant.COMMA)).collect(Collectors.toSet());
                // 从用户提供的列中分离出分区列
                partitionColumns.addAll(tableColumns.stream()
                    .filter(c -> userPartitionNames.contains(c.getColumnName())).collect(Collectors.toList()));
                // 剩下的就是普通列
                normalColumns = tableColumns.stream().filter(c -> !userPartitionNames.contains(c.getColumnName()))
                    .collect(Collectors.toList());
            } else {
                // 如果用户没有提供分区键，则所有列都是普通列
                normalColumns = tableColumns;
            }
            request.setColumnInfos(normalColumns); // 假设 request 中设置普通列的方法是 setColumnInfos
            request.setPartitionColumnInfos(partitionColumns);
            params.add(request);
        });
        return params;
    }

    @Override
    public int[] runSql(DataSourceInfo ds, List<RunSqlParam> params) {
        if (CollectionUtil.isNotEmpty(params)) {
            params.stream().forEach(m -> {
                if (m.getRunSql().toLowerCase(Locale.ROOT).contains("decimal")) {
                    m.setDefaultSet("set odps.sql.decimal.odps2=true;");
                }
            });
        }
        return mapper.runSql(ds, params);
    }

    @Override
    public List<Record> execSql(DataSourceInfo ds, List<RunSqlParam> params) {
        if (CollectionUtil.isEmpty(params)) {
            return new ArrayList<>();
        }
        Odps odps = createOpdsClient(ds);
        List<Record> allRecords = new ArrayList<>();
        for (RunSqlParam param : params) {
            if (StrUtil.isBlank(param.getRunSql())) {
                continue;
            }
            try {
                Instance instance = SQLTask.run(odps, param.getRunSql());
                log.info("SQL 任务已提交，Instance ID: {}", instance.getId());
                instance.waitForSuccess();
                log.info("SQL 任务执行完成");
                List<Record> records = SQLTask.getResult(instance);
                if (CollectionUtil.isNotEmpty(records)) {
                    allRecords.addAll(records);
                } else {
                    log.info("SQL 执行成功，但未返回结果集（可能是 DDL/DML 语句）");
                }
            } catch (OdpsException e) {
                log.error("执行 MaxCompute SQL 失败 - SQL: {}, Endpoint: {}, Project: {}, 错误: {}",
                        param.getRunSql(), odps.getEndpoint(), odps.getDefaultProject(), e.getMessage(), e);
                throw new RuntimeException("执行MaxCompute SQL失败: " + e.getMessage(), e);
            }
        }
        return allRecords;
    }




    /**
     * 获取表的更新语句.
     *
     * @param info
     *            数据源信息
     * @param compareResultDto
     *            表字段对比信息
     * @return 修改表DDL
     */
    @Override
    public String getAlterTableSql(DataSourceInfo info, TableColumnInfoCompareResultDto compareResultDto) {
        StringBuilder sb = new StringBuilder();

        String tableName = compareResultDto.getTargetColumns().get(0).getTableName();
        compareResultDto.getSourceColumns().forEach(m -> {
            m.setTableName(tableName);
        });
        sb.append("--").append(tableName).append("表结构修改").append(LINE_FEED);
        // 处理新增字段
        List<TableColumnInfoCompareInfo> newColumns = compareResultDto.getSourceColumns().stream()
            .filter(col -> col.getCompareResult() == TableColumnCompareEnum.NEW).collect(Collectors.toList());
        if (!newColumns.isEmpty()) {
            newColumns.sort(Comparator.comparing(TableColumnInfoCompareInfo::getColumnSerial));
            // Maxcompute的ADD COLUMNS可以批量添加，但JFinal Enjoy模板的迭代生成可能需要每个单独调用模板。
            // 这里为了简化，我们为每个新增列生成一个 ADD COLUMNS 语句
            // 或者可以设计一个模板批量添加，但更通用的做法是每个操作对应一个语句
            for (TableColumnInfoCompareInfo col : newColumns) {
                Map<String, Object> params = new HashMap<>();
                params.put("tableName", tableName);
                params.put("columnName", col.getColumnName());
                params.put("columnType", col.getFullColumnType());
                params.put("columnDesc", col.getColumnDesc());
                sb.append(builder.renderSql(support().getType() + ".alterTable_addColumn", params).getSql())
                    .append(LINE_FEED);
            }
        }

        // 处理存在差异的字段 (修改类型或注释)
        List<TableColumnInfoCompareInfo> differentColumns = compareResultDto.getSourceColumns().stream()
            .filter(col -> col.getCompareResult() == TableColumnCompareEnum.DIFFERENT).collect(Collectors.toList());

        if (!differentColumns.isEmpty()) {
            for (TableColumnInfoCompareInfo col : differentColumns) {
                // 对于 DIFFERNET，我们假设列名不变，只修改类型或注释
                Map<String, Object> params = new HashMap<>();
                params.put("tableName", tableName);
                params.put("oldColumnName", col.getColumnName()); // 旧列名和新列名相同
                params.put("newColumnName", col.getColumnName()); // 旧列名和新列名相同
                params.put("newColumnType", col.getFullColumnType());
                params.put("newColumnDesc", col.getColumnDesc());
                sb.append(builder.renderSql(support().getType() + ".alterTable_changeColumn", params).getSql())
                    .append(LINE_FEED);
            }
        }

        // 处理缺失字段 (DELETE)。
        // Maxcompute 不直接支持 ALTER TABLE DROP COLUMN。
        // 这种情况下，通常需要更复杂的策略，例如：
        // 1. 如果是开发环境，可能允许手动删除或重建表。
        // 2. 如果是生产环境，可能需要数据迁移（CTAS）或保留列。
        // 鉴于此，我们在这里不生成 DROP COLUMN 的 DDL。
        // 如果你需要处理这种情况，你需要考虑更复杂的逻辑，可能涉及 CREATE TABLE AS SELECT (CTAS)
        // maxcompute 不让修改字段类型，只能修改字段注释
        return sb.toString();
    }

    @Override
    public DataSourceInfo transformDataSourceInfo(DataSourceInfoEntity dsEntity) {
        DataSourceInfo ds = new DataSourceInfo();
        BeanUtils.copyProperties(dsEntity, ds);
        // 判断密码有没有修改？
        if (StrUtil.isNotEmpty(ds.getPassword())) {
            if (AesUtil.isSecret(ds.getPassword())) {
                ds.setPassword(AesUtil.decrypt(ds.getPassword()));
            }
        }
        ds.setConnectType(ConnectTypeEnum.JDBC.getConnectType());
        ds.setDriverClass(DatabaseTypeEnum.MaxcomputeDriver.DEFAULT.getDriverClassName());
        String address = String.format("%s%s", "jdbc:odps:https://", ds.getHost());
        String jdbcUrl = String.format("%s?project=%s&%s&%s", address, ds.getDatabaseName(),
            parseExtendParam(dsEntity.getExtendParam()), "settings={\"odps.namespace.schema\":\"true\"}");
        if (jdbcUrl.endsWith(SystemConstant.QUESTION_MARK)) {
            ds.setJdbcUrl(jdbcUrl.substring(0, jdbcUrl.length() - 1));
        } else {
            ds.setJdbcUrl(jdbcUrl);
        }
        return ds;
    }
}

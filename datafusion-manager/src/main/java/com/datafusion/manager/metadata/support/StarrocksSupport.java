package com.datafusion.manager.metadata.support;

import cn.hutool.core.util.StrUtil;
import com.aliyun.odps.data.Record;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.common.type.TypeInfoParser;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.utils.MathUtil;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import com.datafusion.manager.metadata.enums.StarrocksTableModel;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.DataSourceExtendParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.StarrocksTableColumn;
import com.datafusion.manager.metadata.support.model.StarrocksTableCreateParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.StarrocksMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.datafusion.common.constant.SystemConstant.LINE_FEED;


/**
 * Starrocks 数据库服务.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/28
 * @since 3.7.2, 2024/11/28
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class StarrocksSupport extends AbstractJdbcSupport<List<StarrocksTableColumn>> {
    /**
     * StarRocks 元数据 Mapper.
     */
    private final StarrocksMapper mapper;
    
    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder;
    
    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.STARROCKS;
    }
    
    @Override
    public List<DataSourceExtendParam> getDefaultExtendParams() {
        List<DataSourceExtendParam> dataSourceExtendParams = new ArrayList<>(super.getDefaultExtendParams());
        DataSourceExtendParam rewriteBatchedStatements = DataSourceExtendParam.builder().name("批量语句执行")
                .identifier("rewriteBatchedStatements").value("true").defaultValue("true")
                .options(Arrays.asList("true", "false")).build();
        dataSourceExtendParams.add(rewriteBatchedStatements);
        return dataSourceExtendParams;
    }
    
    @Override
    protected String getDefaultDriverClass() {
        return DatabaseTypeEnum.MySQLDriver.V8.getDriverClassName();
    }
    
    /**
     * 生成JDBC连接串.
     * jdbc:mysql://ip:port/database?characterEncoding=UTF-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai
     *
     * @param info 数据源实体信息
     * @return Postgres数据源
     */
    
    @Override
    protected String generateJdbcUrl(DataSourceInfo info) {
        String jdbcUrl = "jdbc:mysql://"
                + info.getHost()
                + SystemConstant.COLON
                + info.getPort() + SystemConstant.VIRGULE
                + info.getDatabaseName();
        // 使用新的工具类解析扩展参数
        String paramsString = super.parseExtendParam(info.getExtendParam());
        
        // 只有在确实有参数时才添加 '?'
        if (StrUtil.isNotBlank(paramsString)) {
            return jdbcUrl + SystemConstant.QUESTION_MARK + paramsString;
        } else {
            return jdbcUrl;
        }
    }
    
    /**
     * 测试数据源连接.
     *
     * @param ds 数据源信息
     * @return 测试连接结果
     */
    @Override
    public boolean tryConnect(DataSourceInfo ds) {
        try {
            return 1 == mapper.tryConnect(ds);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    protected List<StarrocksTableColumn> queryMetaData(DataSourceInfo ds, List<String> tableNames) {
        List<StarrocksTableColumn> metaData = mapper.getMetaData(ds, ds.getSchemaName(), tableNames);
        if (CollectionUtils.isNotEmpty(metaData)) {
            metaData.forEach(mtc -> {
                if (StringUtils.isNotBlank(mtc.getPartitionKey())) {
                    mtc.setPartitionKey(mtc.getPartitionKey().replaceAll(SystemConstant.BACKTICK, ""));
                }
                
                if (StringUtils.isNotBlank(mtc.getBucketKey())) {
                    mtc.setBucketKey(mtc.getBucketKey().replaceAll(SystemConstant.BACKTICK, ""));
                }
            });
        }
        return metaData;
    }
    
    /**
     * 根据数据库表字段信息转换元数据信息.
     *
     * @param ds   数据源信息
     * @param list 数据库表字段信息
     * @return 元数据信息
     */
    @Override
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<StarrocksTableColumn> list) {
        Map<String, TableInfo> tableMap = new HashMap<>();
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(ds.getDatabaseType()));
        for (StarrocksTableColumn mtc : list) {
            String tableName = mtc.getTableName();
            if (tableMap.get(tableName) == null) {
                // 表
                TableInfo table = tableMap.computeIfAbsent(tableName, tab -> new TableInfo());
                table.setSchemaId(ds.getId());
                table.setTableName(tableName);
                table.setTableDesc(mtc.getTableDesc());
                table.setIsView(mtc.getIsView());
                table.setViewDef(mtc.getViewDef());
                table.setIsModify(false);
                
                //存储表属性
                String tableProperties = mtc.getProperties();
                Properties properties = null;
                if (StrUtil.isNotEmpty(tableProperties)) {
                    try {
                        properties = JacksonUtils.str2Bean(tableProperties, Properties.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
                if (properties == null) {
                    properties = new Properties();
                }
                if (StrUtil.isNotEmpty(mtc.getPartitionKey())) {
                    properties.put(TablePropertiesOptions.PARTITION_KEYS.key(), mtc.getPartitionKey());
                }
                if (StrUtil.isNotEmpty(mtc.getBucketKey())) {
                    properties.put(TablePropertiesOptions.BUCKET_KEYS.key(), mtc.getBucketKey());
                }
                if (StrUtil.isNotEmpty(mtc.getDistributeBucket())) {
                    properties.put(TablePropertiesOptions.BUCKET_NUM.key(), mtc.getDistributeBucket());
                }
                if (StrUtil.isNotEmpty(mtc.getTableModel())) {
                    properties.put(TablePropertiesOptions.TABLE_MODEL.key(), mtc.getTableModel());
                }
                if (StrUtil.isNotEmpty(mtc.getPrimaryKey())) {
                    properties.put(TablePropertiesOptions.PRIMARY_KEYS.key(), mtc.getPrimaryKey());
                }
                table.setTableProperties(properties);
            }
            
            // 字段
            List<TableColumnInfo> columns = columnMap.computeIfAbsent(tableName, col -> new ArrayList<>());
            TableColumnInfo column = new TableColumnInfo();
            columns.add(column);
            
            column.setTableName(tableName);
            column.setColumnName(mtc.getColumnName());
            column.setColumnDesc(mtc.getColumnDesc());
            column.setColumnSerial(mtc.getOrdinalPosition().intValue());
            column.setColumnType(mtc.getColumnType());
            column.setColumnLength(null == mtc.getColumnLength() ? null : mtc.getColumnLength().intValue());
            column.setColumnPrecision(null == mtc.getColumnPrecision() ? null : mtc.getColumnPrecision().intValue());
            column.setScale(null == mtc.getScale() ? null : mtc.getScale().intValue());
            column.setIsNullable(mtc.getIsNullable());
            Boolean isPrimary = mtc.getIsPrimary();
            column.setIsPrimary(isPrimary);
            //在建表等场景下,需要全字段
            if (StrUtil.isEmpty(mtc.getFullColumnType())) {
                TypeInfo typeInfo = parser.parse(column.getColumnType(), column.getColumnLength(), column.getColumnPrecision(),
                        column.getScale());
                column.setFullColumnType(typeInfo.getFullFieldType());
            } else {
                column.setFullColumnType(mtc.getFullColumnType());
            }
        }
        
        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        return metadata;
    }
    
    @Override
    public String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        List<StarrocksTableCreateParam> params = buildCreateTableParam(metaData);
        StringBuilder sb = new StringBuilder();
        for (StarrocksTableCreateParam param : params) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("tableName", param.getTableName());
            paramMap.put("columnInfos", param.getColumnInfos());
            paramMap.put("tableDesc", param.getTableDesc());
            paramMap.put("createKey", param.getCreateKey());
            paramMap.put("partitionKeys", param.getPartitionKeys());
            paramMap.put("primaryKeys", param.getPrimaryKeys());
            paramMap.put("bucketKeys", param.getBucketKeys());
            paramMap.put("bucketNum", param.getBucketNum());
            String sql = builder.renderSql(ds.getDatabaseType() + ".createTable", paramMap).getSql();
            sb.append("--").append(param.getTableName()).append("表结构创建").append(LINE_FEED);
            sb.append(sql).append(System.lineSeparator());
        }
        //获取所有sql语句
        return new String(sb);
    }
    
    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        return mapper.countByTable(ds, ds.getSchemaName(), tableName);
    }
    
    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        return MathUtil.fileSizeConverter(mapper.countSizeByTable(ds, ds.getSchemaName(), tableName));
    }
    
    /**
     * 底层构建starrocks建表对象,其他逻辑在业务中处理.
     *
     * @param metaDataInfo 元数据信息
     * @return 建表结果
     */
    private List<StarrocksTableCreateParam> buildCreateTableParam(MetaDataInfo metaDataInfo) {
        Map<String, List<TableColumnInfo>> groupedColumnMap = metaDataInfo.getColumns();
        List<StarrocksTableCreateParam> params = new ArrayList<>();
        metaDataInfo.getTables().forEach(table -> {
            StarrocksTableCreateParam request = new StarrocksTableCreateParam();
            request.setTableName(table.getTableName());
            request.setTableDesc(table.getTableDesc());
            Properties tableProperties = table.getTableProperties();
            if (tableProperties == null) {
                tableProperties = new Properties();
            }
            String tableModel = tableProperties.getProperty(TablePropertiesOptions.TABLE_MODEL.key());
            if (StrUtil.isEmpty(tableModel)) {
                tableModel = StarrocksTableModel.PRIMARY_TABLE.getModelType();
            }
            request.setTableModel(tableModel);
            //主键,建表必须要主键,不管是哪种模型,必要的参数在业务方法中,处理
            request.setPrimaryKeys(tableProperties.getProperty(TablePropertiesOptions.PRIMARY_KEYS.key()));
            request.setPartitionKeys(tableProperties.getProperty(TablePropertiesOptions.PARTITION_KEYS.key()));
            //分桶最后默认值值
            String bucketNum = tableProperties.getProperty(TablePropertiesOptions.BUCKET_NUM.key());
            bucketNum = StrUtil.isEmpty(bucketNum) ? TablePropertiesOptions.BUCKET_NUM.defaultValue() : bucketNum;
            request.setBucketNum(Integer.valueOf(bucketNum));
            //分布键最后设置,跟主键保持也一致
            String bucketKeys = tableProperties.getProperty(TablePropertiesOptions.BUCKET_KEYS.key());
            bucketKeys = StrUtil.isEmpty(bucketKeys) ? request.getPrimaryKeys() : bucketKeys;
            request.setBucketKeys(bucketKeys);
            List<TableColumnInfo> tableColumns = groupedColumnMap.getOrDefault(table.getTableName(), new ArrayList<>());
            request.setColumnInfos(tableColumns);
            params.add(request);
        });
        return params;
    }
    
    @Override
    public List<Map<String, Object>> getDataPreview(DataSourceInfo ds, DataPreviewQuery condition) {
        return mapper.getDataPreview(ds, condition);
    }
    
    /**
     * 获取建表的字段定义.
     * <pre>
     * `id` string comment '主键'
     * </pre>
     *
     * @param columns 字段信息集合
     * @return 字段定义
     */
    private List<String> getColumnsOnTableCreate(List<TableColumnInfo> columns) {
        
        List<TableColumnInfo> sortedColumns = columns.stream()
                .sorted(Comparator.comparing(TableColumnInfo::getIsPrimary).reversed()) // 按主键降序排列
                .collect(Collectors.toList());
        
        List<String> columnDefs = new ArrayList<>();
        sortedColumns.forEach(col -> {
            String columnType = StringUtils.defaultIfBlank(col.getColumnType(), "STRING");
            columnType = concatColumnTypeLength(columnType, col.getColumnLength(), col.getColumnPrecision());
            
            StringBuilder builder = new StringBuilder(SystemConstant.BACKTICK).append(col.getColumnName())
                    .append(SystemConstant.BACKTICK).append(SystemConstant.SPACE).append(columnType);
            
            if (StringUtils.isNotBlank(col.getColumnDesc())) {
                builder.append(SystemConstant.SPACE).append("COMMENT").append(SystemConstant.SPACE)
                        .append(SystemConstant.SINGLE_QUOTES).append(col.getColumnDesc())
                        .append(SystemConstant.SINGLE_QUOTES);
            }
            
            columnDefs.add(builder.toString());
        });
        
        return columnDefs;
    }
    
    /**
     * 拼接字段类型及长度.
     *
     * @param columnType      字段类型
     * @param columnLength    字段长度
     * @param columnPrecision 字段精度
     * @return varchar(10)
     */
    private String concatColumnTypeLength(String columnType, Integer columnLength, Integer columnPrecision) {
        
        // 定义需要处理的数据类型
        if (columnLength != null) {
            if (columnPrecision == null) {
                return String.format("%s(%s)", columnType, columnLength);
            } else {
                return String.format("%s(%s,%s)", columnType, columnLength, columnPrecision);
            }
        }
        
        // 如果转换后的类型是 VARCHAR 并且 columnLength 为 null，则添加默认长度
        /*if (DbTypeEnum.VARCHAR.name().equalsIgnoreCase(columnType)) {
            return columnType + SystemConstant.LEFT_PARENTHESIS + VARCHAR_DEFAULT_LENGTH
                    + SystemConstant.RIGHT_PARENTHESIS;
        }
        
        if ((DbTypeEnum.NUMERIC.name().equalsIgnoreCase(columnType) || DbTypeEnum.DECIMAL.name()
                .equalsIgnoreCase(columnType))) {
            return String.format("%s(%s,%s)", columnType, DECIMAL_DEFAULT_LENGTH, DECIMAL_DEFAULT_PRECISION);
        }*/
        
        return columnType;
    }
    
    @Override
    public int[] runSql(DataSourceInfo ds, List<RunSqlParam> params) {
        return mapper.runSql(ds, params);
    }

    @Override
    public List<Record> execSql(DataSourceInfo ds, List<RunSqlParam> params) {
        return null;
    }
}

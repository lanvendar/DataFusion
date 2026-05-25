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
import com.datafusion.manager.metadata.enums.Relkind;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.HologresTableColumn;
import com.datafusion.manager.metadata.support.model.HologresTableCreateParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.HologresMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Postgres数据库服务.
 *
 * @author david
 * @version 3.6.4, 2024/9/9
 * @since 3.6.4, 2024/9/9
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class HologresSupport extends AbstractJdbcSupport<List<HologresTableColumn>> {
    
    /**
     * PG数据眼Mapper.
     */
    private final HologresMapper mapper;
    
    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder;
    
    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.HOLOGRES;
    }
    
    @Override
    protected String getDefaultDriverClass() {
        return DatabaseTypeEnum.PostgresDriver.DEFAULT.getDriverClassName();
    }
    
    /**
     * 生成JDBC连接URL.
     * jdbc:postgresql://ip:port/database?characterEncoding=UTF-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai
     *
     * @param info 数据源实体信息
     * @return Hologres JdbcUrl
     */
    @Override
    protected String generateJdbcUrl(DataSourceInfo info) {
        String jdbcUrl = "jdbc:postgresql://"
                + info.getHost()
                + SystemConstant.COLON
                + info.getPort()
                + SystemConstant.VIRGULE
                + info.getDatabaseName()
                + SystemConstant.QUESTION_MARK
                + "currentSchema="
                + info.getSchemaName();
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
            Integer connected = mapper.tryConnect(ds);
            return 1 == connected;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    protected List<HologresTableColumn> queryMetaData(DataSourceInfo dsInfo, List<String> tableNames) {
        return mapper.getMetaData(dsInfo, dsInfo.getSchemaName(), tableNames);
    }
    
    /**
     * 根据数据库表字段信息转换元数据信息.
     *
     * @param ds   数据源信息
     * @param list 数据库表字段信息
     * @return 元数据信息
     */
    @Override
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<HologresTableColumn> list) {
        Map<String, TableInfo> tableMap = new HashMap<>();
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(ds.getDatabaseType()));
        for (HologresTableColumn ptc : list) {
            String tableName = ptc.getTableName();
            if (tableMap.get(tableName) == null) {
                TableInfo table = tableMap.computeIfAbsent(tableName, t -> new TableInfo());
                table.setSchemaId(ds.getId());
                table.setTableName(ptc.getTableName());
                table.setTableDesc(ptc.getTableDesc());
                table.setIsView(ptc.getIsView());
                table.setViewDef(ptc.getViewDef());
                table.setIsModify(false);
                //获取从元数据获取的表属性
                String tablePropertiesStr = ptc.getTableProperties();
                Properties properties = null;
                if (StrUtil.isNotEmpty(tablePropertiesStr)) {
                    try {
                        properties = JacksonUtils.str2Bean(tablePropertiesStr, Properties.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
                if (properties == null) {
                    properties = new Properties();
                }
                
                //封装表属性
                String partitionKeys = ptc.getPartitionKeys();
                if (StrUtil.isNotEmpty(partitionKeys)) {
                    properties.put(TablePropertiesOptions.PARTITION_KEYS.key(), partitionKeys);
                }
                properties.put(TablePropertiesOptions.REL_KIND.key(), ptc.getRelkind());
                table.setTableProperties(properties);
            }
            
            // 字段
            List<TableColumnInfo> columns = columnMap.computeIfAbsent(tableName, col -> new ArrayList<>());
            TableColumnInfo column = new TableColumnInfo();
            columns.add(column);
            column.setTableName(tableName);
            column.setColumnName(ptc.getColumnName());
            column.setColumnDesc(ptc.getColumnDesc());
            column.setColumnSerial(ptc.getOrdinalPosition());
            column.setColumnType(ptc.getColumnType());
            column.setColumnLength(ptc.getColumnLength());
            column.setColumnPrecision(ptc.getColumnPrecision());
            column.setScale(ptc.getScale());
            column.setIsNullable(ptc.getIsNullable());
            column.setIsPrimary(ptc.getIsPrimary());
            //在建表等场景下,需要全字段
            if (StrUtil.isEmpty(ptc.getFullColumnType())) {
                TypeInfo typeInfo = parser.parse(column.getColumnType(), column.getColumnLength(), column.getColumnPrecision(),
                        column.getScale());
                column.setFullColumnType(typeInfo.getFullFieldType());
            } else {
                column.setFullColumnType(ptc.getFullColumnType());
            }
        }
        
        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        return metadata;
    }
    
    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        String relKind = mapper.getTableRelkind(ds, ds.getSchemaName(), tableName);
        if (Relkind.NORMAL_TABLE.getType().equals(relKind) || Relkind.PARTITION_PARENT_TABLE.getType().equals(relKind)) {
            return mapper.countByTable(ds, ds.getSchemaName(), tableName);
        }
        //holo外表,直接返回结果 0
        return super.getTableCount(ds, tableName);
    }
    
    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        String relKind = mapper.getTableRelkind(ds, ds.getSchemaName(), tableName);
        if (Relkind.NORMAL_TABLE.getType().equals(relKind) || Relkind.PARTITION_PARENT_TABLE.getType().equals(relKind)) {
            long size = mapper.countSizeByTable(ds, tableName);
            return MathUtil.fileSizeConverter(size);
        }
        //holo外表,直接返回结果 0KB
        return super.getTableSize(ds, tableName);
    }
    
    @Override
    public String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        List<HologresTableCreateParam> params = buildCreateTableParam(metaData);
        StringBuilder sb = new StringBuilder();
        for (HologresTableCreateParam param : params) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("tableName", param.getTableName());
            paramMap.put("columnInfos", param.getColumnInfos());
            if (StrUtil.isNotEmpty(param.getTableDesc())) {
                paramMap.put("tableDesc", param.getTableDesc());
            }
            paramMap.put("partitionKeys", param.getPartitionKeys());
            paramMap.put("primaryKeys", param.getPrimaryKeys());
            paramMap.put("tableProperties", param.getTableProperties());
            String sql = builder.renderSql(ds.getDatabaseType() + ".createTable", paramMap).getSql();
            sb.append(sql).append(System.lineSeparator());
        }
        //获取所有sql语句
        return new String(sb);
    }
    
    /**
     * 底层构建hologres建表对象,其他逻辑在业务中处理.
     *
     * @param metaDataInfo 元数据信息
     * @return 建表请求对象
     */
    private List<HologresTableCreateParam> buildCreateTableParam(MetaDataInfo metaDataInfo) {
        List<HologresTableCreateParam> params = new ArrayList<>();
        Map<String, List<TableColumnInfo>> columns = metaDataInfo.getColumns();
        metaDataInfo.getTables().stream().forEach(table -> {
            HologresTableCreateParam param = new HologresTableCreateParam();
            //处理主键字段
            Properties properties = table.getTableProperties();
            String primaryKeys = properties.getProperty(TablePropertiesOptions.HOLOGRE_PRIMARY_KEYS.key());
            properties.remove(TablePropertiesOptions.HOLOGRE_PRIMARY_KEYS.key());
            param.setPrimaryKeys(primaryKeys);
            //处理分区字段
            String partitionKeys = table.getPartitionKeys();
            if (StrUtil.isNotEmpty(partitionKeys)) {
                param.setPartitionKeys(partitionKeys);
                properties.remove(TablePropertiesOptions.PARTITION_KEYS.key());
            }
            //处理表分类
            String rkind = properties.getProperty(TablePropertiesOptions.REL_KIND.key());
            if (StrUtil.isNotEmpty(rkind)) {
                properties.remove(TablePropertiesOptions.REL_KIND.key());
            }
            //剩下表原始属性
            param.setTableProperties(properties);
            List<TableColumnInfo> columnInfos = columns.getOrDefault(table.getTableName(), Collections.emptyList());
            param.setTableName(table.getTableName());
            param.setTableDesc(table.getTableDesc());
            param.setColumnInfos(columnInfos);
            params.add(param);
        });
        return params;
    }
    
    @Override
    public List<Map<String, Object>> getDataPreview(DataSourceInfo ds, DataPreviewQuery condition) {
        /*String tableName = condition.getTableName();
        List<SelectListColumn> columns = condition.getColumns();
        String whereSql = buildWhereClause(condition.getQueryConditions());
        String orderSql = buildOrderByClause(condition.getOrderConditions());
        int limit = condition.getLimit();*/
        return mapper.getDataPreview(ds, condition);
    }
    
    @Override
    public int[] runSql(DataSourceInfo ds, List<RunSqlParam> params) {
        return mapper.runSql(ds, params);
    }

    @Override
    public List<Record> execSql(DataSourceInfo ds, List<RunSqlParam> params) {
        log.warn("Hologres execSql 当前不支持返回结果集, 将返回空列表. 请通过开发侧异步执行器获取结果.");
        return new ArrayList<>();
    }
}

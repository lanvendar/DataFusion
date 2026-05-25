package com.datafusion.manager.metadata.support;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.odps.data.Record;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.common.type.TypeInfoParser;
import com.datafusion.common.utils.MathUtil;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.PostgreTableCreateParam;
import com.datafusion.manager.metadata.support.model.PostgresTableColumn;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.PostgresMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

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
public class PostgresSupport extends AbstractJdbcSupport<List<PostgresTableColumn>> {
    
    /**
     * PG数据眼Mapper.
     */
    private final PostgresMapper mapper;
    
    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder;
    
    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.POSTGRES;
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
     * @return Postgres数据源
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
    protected List<PostgresTableColumn> queryMetaData(DataSourceInfo dsInfo, List<String> tableNames) {
        return mapper.getMetaData(dsInfo, dsInfo.getSchemaName(), tableNames);
    }
    
    /**
     * countTables.
     *
     * @param dsEntity dsEntity
     * @return Long
     */
    public Long countTables(DataSourceInfoEntity dsEntity) {
        return mapper.countTables(transformDataSourceInfo(dsEntity), dsEntity.getSchemaName());
    }
    
    /**
     * 根据数据库表字段信息转换元数据信息.
     *
     * @param ds   数据源信息
     * @param list 数据库表字段信息
     * @return 元数据信息
     */
    @Override
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<PostgresTableColumn> list) {
        Map<String, TableInfo> tableMap = new HashMap<>();
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(ds.getDatabaseType()));
        List<String> primaryList = null;
        for (PostgresTableColumn ptc : list) {
            String tableName = ptc.getTableName();
            if (tableMap.get(tableName) == null) {
                TableInfo table = tableMap.computeIfAbsent(tableName, t -> new TableInfo());
                table.setSchemaId(ds.getId());
                table.setTableName(ptc.getTableName());
                table.setTableDesc(ptc.getTableDesc());
                table.setIsView(ptc.getIsView());
                table.setViewDef(ptc.getViewDef());
                table.setIsModify(false);
                String primaryKeys = ptc.getPrimaryKeys();
                if (StrUtil.isNotEmpty(primaryKeys)) {
                    Properties properties = new Properties();
                    properties.put(TablePropertiesOptions.PRIMARY_KEYS.key(), ptc.getPrimaryKeys());
                    table.setTableProperties(properties);
                    primaryList = Arrays.stream(primaryKeys.split(",")).collect(Collectors.toList());
                }
            }
            
            // 字段
            List<TableColumnInfo> columns = columnMap.computeIfAbsent(tableName, col -> new ArrayList<>());
            TableColumnInfo column = new TableColumnInfo();
            columns.add(column);
            BeanUtils.copyProperties(ptc, column);
            //在建表等场景下,需要全字段
            TypeInfo typeInfo = parser.parse(column.getColumnType(), column.getColumnLength(), column.getColumnPrecision(),
                    column.getScale());
            column.setFullColumnType(typeInfo.getFullFieldType());
            column.setIsPrimary(CollectionUtil.isNotEmpty(primaryList) && primaryList.contains(column.getColumnName()));
            column.setColumnSerial(ptc.getOrdinalPosition());
        }
        
        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        return metadata;
    }
    
    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        return mapper.countByTable(ds, ds.getSchemaName(), tableName);
    }
    
    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        long size = mapper.countSizeByTable(ds, tableName);
        return MathUtil.fileSizeConverter(size);
    }
    
    @Override
    public String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        List<PostgreTableCreateParam> params = buildCreateTableParam(metaData);
        StringBuilder sb = new StringBuilder();
        for (PostgreTableCreateParam param : params) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("tableName", param.getTableName());
            paramMap.put("columnInfos", param.getColumnInfos());
            if (StrUtil.isNotEmpty(param.getTableDesc())) {
                paramMap.put("tableDesc", param.getTableDesc());
            }
            paramMap.put("primaryKeys", param.getPrimaryKeys());
            
            String sql = builder.renderSql(support().getType() + ".createTable", paramMap).getSql();
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
    private List<PostgreTableCreateParam> buildCreateTableParam(MetaDataInfo metaDataInfo) {
        List<PostgreTableCreateParam> params = new ArrayList<>();
        Map<String, List<TableColumnInfo>> columns = metaDataInfo.getColumns();
        metaDataInfo.getTables().stream().forEach(table -> {
            PostgreTableCreateParam param = new PostgreTableCreateParam();
            //处理主键字段
            Properties properties = table.getTableProperties();
            if (properties == null) {
                properties = new Properties();
            }
            String primaryKeys = properties.getProperty(TablePropertiesOptions.PRIMARY_KEYS.key());
            properties.remove(TablePropertiesOptions.PRIMARY_KEYS.key());
            param.setPrimaryKeys(primaryKeys);
            
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
        log.warn("Postgres execSql 当前不支持返回结果集, 将返回空列表. 请通过开发侧异步执行器获取结果.");
        return new ArrayList<>();
    }
}

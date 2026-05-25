package com.datafusion.manager.metadata.support;

import cn.hutool.core.util.StrUtil;
import com.aliyun.odps.data.Record;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.DataSourceExtendParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.MysqlTableColumn;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.MySqlMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL数据库服务.
 *
 * @author david
 * @version 3.6.4, 2024/9/9
 * @since 3.6.4, 2024/9/9
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class MysqlSupport extends AbstractJdbcSupport<List<MysqlTableColumn>> {
    
    /**
     * MySQL元数据Mapper.
     */
    private final MySqlMapper mapper;
    
    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.MYSQL;
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
    
    @Override
    protected String generateJdbcUrl(DataSourceInfo info) {
        String jdbcUrl = "jdbc:mysql://"
                + info.getHost()
                + SystemConstant.COLON
                + info.getPort()
                + SystemConstant.VIRGULE
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
            return 1L == mapper.tryConnect(ds);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    protected List<MysqlTableColumn> queryMetaData(DataSourceInfo dsInfo, List<String> tableNames) {
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
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<MysqlTableColumn> list) {
        Map<String, TableInfo> tableMap = new HashMap<>();
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        
        for (MysqlTableColumn mtc : list) {
            String tableName = mtc.getTableName();
            
            // 表
            TableInfo table = tableMap.computeIfAbsent(tableName, tab -> new TableInfo());
            table.setSchemaId(ds.getId());
            table.setTableName(tableName);
            table.setTableDesc(mtc.getTableDesc());
            table.setIsView(null != mtc.getIsView() && mtc.getIsView() == 1);
            table.setViewDef(mtc.getViewDef());
            table.setIsModify(false);
            
            //todo 属性存储在properties
            boolean isPrimary = null != mtc.getIsPrimary() && mtc.getIsPrimary() == 1;
            if (isPrimary) {
            /*  if (null != table.getPrimaryKeys()) {
                    String key = table.getPrimaryKeys() + SystemConstant.COMMA + mtc.getColumnName();
                    table.setPrimaryKeys(key);
                } else {
                    table.setPrimaryKeys(mtc.getColumnName());
                }*/
            }
            
            // 字段
            List<TableColumnInfo> columns = columnMap.computeIfAbsent(table.getTableName(), col -> new ArrayList<>());
            TableColumnInfo column = new TableColumnInfo();
            columns.add(column);
            
            column.setTableName(tableName);
            column.setColumnName(mtc.getColumnName());
            column.setColumnDesc(mtc.getColumnDesc());
            column.setColumnSerial(mtc.getOrdinalPosition().intValue());
            column.setColumnType(mtc.getColumnType());
            column.setColumnLength(null == mtc.getColumnLength() ? null : mtc.getColumnLength().intValue());
            column.setColumnPrecision(null == mtc.getColumnPrecision() ? null : mtc.getColumnPrecision().intValue());
            column.setIsNullable(null != mtc.getIsNullable() && mtc.getIsNullable() == 1);
            column.setIsPrimary(isPrimary);
        }
        
        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        
        return metadata;
    }
    
    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        BigInteger total = mapper.countByTable(ds, ds.getSchemaName(), tableName);
        return total.longValue();
    }
    
    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        return mapper.countSizeByTable(ds, tableName);
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
        return null;
    }
}

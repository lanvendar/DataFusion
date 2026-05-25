/*
 * Copyright © 2000-2024 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

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
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.DmTableColumn;
import com.datafusion.manager.metadata.support.model.DmTableCreateParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.metadata.support.sql.DmMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.datafusion.common.constant.SystemConstant.LINE_FEED;

/**
 * Oracle数据库服务.
 *
 * @author david
 * @version 3.6.4, 2024/9/9
 * @since 3.6.4, 2024/9/9
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class DmSupport extends AbstractJdbcSupport<List<DmTableColumn>> {
    
    /**
     * Dm数据源Mapper.
     */
    private final DmMapper mapper;
    
    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder;
    
    @Override
    public DatabaseTypeEnum support() {
        return DatabaseTypeEnum.DM;
    }
    
    @Override
    protected String getDefaultDriverClass() {
        return DatabaseTypeEnum.DmDriver.DEFAULT.getDriverClassName();
    }
    
    /**
     * 生成JdbcUrl.
     * jdbc:dm:thin:@ip:port:orcl?characterEncoding=UTF-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai
     *
     * @param info 数据源信息
     * @return 达梦的 JdbcUrl
     */
    @Override
    protected String generateJdbcUrl(DataSourceInfo info) {
        // 使用 StrUtil.format 拼接基础 URL，更清晰安全
        String jdbcUrl = StrUtil.format("jdbc:dm://{}:{}/{}",
                info.getHost(),
                info.getPort(),
                info.getSchemaName());
        
        // 使用新的工具类解析扩展参数
        String paramsString = super.parseExtendParam(info.getExtendParam());
        
        // 智能拼接：只有在确实有参数时才添加 '?'
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
            return StringUtils.equals("a", mapper.tryConnect(ds));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取数据源信息获取元数据,由具体数据库实现.
     *
     * @param ds         数据源信息
     * @param tableNames 表名称集合
     * @return 各数据库返回的数据库表字段信息
     */
    @Override
    protected List<DmTableColumn> queryMetaData(DataSourceInfo ds, List<String> tableNames) {
        return mapper.getMetaData(ds, ds.getSchemaName(), tableNames);
    }
    
    /**
     * 根据各数据库返回的数据库表字段信息转换元数据信息.
     *
     * @param ds   数据源信息
     * @param list 数据库表字段信息
     * @return 元数据信息
     */
    @Override
    protected MetaDataInfo transformMetaData(DataSourceInfo ds, List<DmTableColumn> list) {
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(ds.getDatabaseType()));
        Map<String, List<DmTableColumn>> sourceTableMap = list.stream()
                .collect(Collectors.groupingBy(DmTableColumn::getTableName));
        Map<String, List<TableColumnInfo>> columnMap = new HashMap<>();
        Map<String, TableInfo> tableMap = new HashMap<>();
        for (Map.Entry<String, List<DmTableColumn>> entry : sourceTableMap.entrySet()) {
            String tableName = entry.getKey();
            List<DmTableColumn> columnList = entry.getValue();
            // 表
            // 表
            TableInfo table = tableMap.computeIfAbsent(tableName, tab -> new TableInfo());
            table.setSchemaId(ds.getId());
            table.setTableName(tableName);
            table.setTableDesc(columnList.get(0).getTableDesc());
            table.setIsView(columnList.get(0).getIsView() != null && columnList.get(0).getIsView().intValue() == 1);
            table.setViewDef(columnList.get(0).getViewDef());
            table.setIsModify(false);
            Properties props = new Properties();
            
            if (StrUtil.isNotEmpty(columnList.get(0).getTablePrimaryKeys())) {
                props.setProperty(TablePropertiesOptions.PRIMARY_KEYS.key(), columnList.get(0).getTablePrimaryKeys());
            }
            
            table.setTableProperties(props);
            for (DmTableColumn otc : columnList) {
                List<TableColumnInfo> columns = columnMap.computeIfAbsent(tableName, col -> new ArrayList<>());
                TableColumnInfo column = new TableColumnInfo();
                columns.add(column);
                
                column.setTableName(otc.getTableName());
                column.setColumnName(otc.getColumnName());
                column.setColumnDesc(otc.getColumnDesc());
                column.setColumnType(otc.getColumnType());
                column.setColumnSerial(null == otc.getOrdinalPosition() ? null : otc.getOrdinalPosition().intValue());
                column.setColumnLength(null == otc.getColumnLength() ? null : otc.getColumnLength().intValue());
                column.setColumnPrecision(null == otc.getColumnPrecision() ? null : otc.getColumnPrecision().intValue());
                column.setScale(null == otc.getScale() ? null : otc.getScale().intValue());
                column.setIsNullable(otc.getIsNullable() != null && otc.getIsNullable() == 1);
                column.setIsPrimary(otc.getIsPrimary() != null && otc.getIsPrimary().intValue() == 1);
                column.setDefaultValue(otc.getColumnDefault());
                //在建表等场景下,需要全字段
                if (StrUtil.isEmpty(otc.getFullColumnType())) {
                    TypeInfo typeInfo = parser.parse(column.getColumnType(), column.getColumnLength(), column.getColumnPrecision(),
                            column.getScale());
                    column.setFullColumnType(typeInfo.getFullFieldType());
                } else {
                    column.setFullColumnType(otc.getFullColumnType());
                }
            }
        }
        
        MetaDataInfo metadata = new MetaDataInfo();
        metadata.setTables(new ArrayList<>(tableMap.values()));
        metadata.setColumns(columnMap);
        
        return metadata;
    }
    
    @Override
    public long getTableCount(DataSourceInfo ds, String tableName) {
        return mapper.countByTable(ds, tableName);
    }
    
    @Override
    public String getTableSize(DataSourceInfo ds, String tableName) {
        return MathUtil.fileSizeConverter(mapper.countSizeByTable(ds, tableName));
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

    @Override
    public String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        List<DmTableCreateParam> createParams = buildCreateTableParam(metaData);
        StringBuilder sb = new StringBuilder();
        if (CollectionUtil.isNotEmpty(createParams)) {
            for (DmTableCreateParam param : createParams) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("tableInfo", param.getTableInfo());
                paramMap.put("columnInfos", param.getColumnInfos());
                paramMap.put("primary_key_name",
                        "PK_" + param.getTableInfo().getTableName() + "_" + param.getTableInfo().getPrimaryKeys().replaceAll(",", "_"));
                
                String sql = builder.renderSql(ds.getDatabaseType() + ".createTable", paramMap).getSql();
                sb.append("--").append(param.getTableInfo().getTableName()).append("表结构创建").append(LINE_FEED);
                sb.append(sql).append(System.lineSeparator());
            }
        }
        return new String(sb);
    }
    
    /**
     * 构建建表语句.
     * @param metaData 元数据
     * @return 达梦建表语句实体
     */
    private List<DmTableCreateParam> buildCreateTableParam(MetaDataInfo metaData) {
        List<DmTableCreateParam> params = new ArrayList<>();
        Map<String, List<TableColumnInfo>> columns = metaData.getColumns();
        metaData.getTables().forEach(table -> {
            DmTableCreateParam request = new DmTableCreateParam();
            request.setTableInfo(table);
            request.setColumnInfos(columns.get(table.getTableName()));
            params.add(request);
        });
        return params;
    }
}

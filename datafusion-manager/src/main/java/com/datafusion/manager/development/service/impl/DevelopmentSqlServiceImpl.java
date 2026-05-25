package com.datafusion.manager.development.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.aliyun.odps.Column;
import com.aliyun.odps.data.Record;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.development.service.DevelopmentSqlService;
import com.datafusion.manager.development.service.sql.SqlScriptUtils;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开发侧SQL执行服务实现.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@Service
@RequiredArgsConstructor
public class DevelopmentSqlServiceImpl implements DevelopmentSqlService {

    /**
     * 元数据-数据库服务.
     */
    private final DataSourceInfoService dataSourceInfoService;

    @Override
    public ExecSqlResultDto execSql(ExecuteCreateTableDto executeCreateTableDto) {
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(executeCreateTableDto.getDatasourceId());
        SqlScriptUtils.checkSqlExecutable(dsEntity);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dsEntity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo dataSourceInfo = databaseTransformService.transformDataSourceInfo(dsEntity);

        List<String> validStatements = SqlScriptUtils.splitAndClean(executeCreateTableDto.getSql());
        if (CollectionUtil.isEmpty(validStatements)) {
            return buildEmptyResult("SQL脚本为空或仅包含注释");
        }

        List<RunSqlParam> params = validStatements.stream()
                .map(sql -> new RunSqlParam(null, sql))
                .collect(Collectors.toList());
        List<Record> records = databaseService.execSql(dataSourceInfo, params);
        return convertRecords(records);
    }

    /**
     * 将ODPS Record结果转换为通用结构.
     *
     * @param records ODPS查询结果
     * @return 通用SQL执行结果
     */
    private ExecSqlResultDto convertRecords(List<Record> records) {
        if (CollectionUtil.isEmpty(records)) {
            return buildEmptyResult("执行成功，未返回结果集");
        }
        Column[] columns = records.get(0).getColumns();
        if (columns == null || columns.length == 0) {
            return buildEmptyResult("执行成功，结果集无列信息");
        }
        List<String> columnNames = Arrays.stream(columns).map(Column::getName).collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Record record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.length; i++) {
                row.put(columnNames.get(i), record.get(i));
            }
            rows.add(row);
        }
        ExecSqlResultDto result = new ExecSqlResultDto();
        result.setColumns(columnNames);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setMessage("执行成功");
        return result;
    }

    /**
     * 构建空结果对象.
     *
     * @param message 提示信息
     * @return 空结果
     */
    private ExecSqlResultDto buildEmptyResult(String message) {
        ExecSqlResultDto result = new ExecSqlResultDto();
        result.setColumns(Collections.emptyList());
        result.setRows(Collections.emptyList());
        result.setRowCount(0);
        result.setMessage(message);
        return result;
    }
}

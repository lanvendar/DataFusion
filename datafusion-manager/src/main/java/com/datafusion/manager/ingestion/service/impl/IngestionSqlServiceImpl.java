package com.datafusion.manager.ingestion.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDetailDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableResultDto;
import com.datafusion.manager.ingestion.service.IngestionSqlService;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.MetaDataQuery;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.TableInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据集成SQL执行服务实现.
 *
 * @author codex
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@Service
@RequiredArgsConstructor
public class IngestionSqlServiceImpl implements IngestionSqlService {

    /**
     * 过滤注释用的正则匹配.
     */
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile("--[^\\n]*");

    /**
     * CREATE TABLE 语句匹配.
     */
    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile("^\\s*CREATE\\s+TABLE\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * CREATE TABLE 表名提取匹配.
     */
    private static final Pattern CREATE_TABLE_NAME_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"a-zA-Z0-9_\\.]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 元数据-数据库服务.
     */
    private final DataSourceInfoService dataSourceInfoService;

    @Override
    public ExecuteCreateTableResultDto executeCreateTable(ExecuteCreateTableDto executeCreateTableDto) {
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(executeCreateTableDto.getDatasourceId());
        //checkTableCreatable(dsEntity);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dsEntity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo dataSourceInfo = databaseTransformService.transformDataSourceInfo(dsEntity);

        String[] sqlStatements = executeCreateTableDto.getSql().split("(?<!\\\\);");
        List<String> createTableStatements = new ArrayList<>();
        List<String> tableNames = new ArrayList<>();
        Set<String> requestTableNameSet = new HashSet<>();
        for (String statement : sqlStatements) {
            String trimmedStatement = cleanSqlStatement(statement.trim());
            if (trimmedStatement.isEmpty()) {
                continue;
            }
            if (!CREATE_TABLE_PATTERN.matcher(trimmedStatement).find()) {
                throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "仅支持CREATE TABLE语句");
            }
            String tableName = getCreateTableName(trimmedStatement);
            if (StrUtil.isBlank(tableName)) {
                throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "建表语句未识别到表名");
            }
            String normalizedTableName = tableName.toLowerCase(Locale.ROOT);
            if (!requestTableNameSet.add(normalizedTableName)) {
                throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "脚本中存在重复建表语句:" + tableName);
            }
            if (isTargetTableExists(databaseService, dataSourceInfo, tableName)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "目标表已存在:" + tableName);
            }
            createTableStatements.add(trimmedStatement);
            tableNames.add(tableName);
        }

        if (CollectionUtil.isEmpty(createTableStatements)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "至少包含一条CREATE TABLE语句");
        }
        List<RunSqlParam> params = createTableStatements.stream().map(sql -> new RunSqlParam(null, sql)).collect(Collectors.toList());
        int[] executeResult = databaseService.runSql(dataSourceInfo, params);

        List<ExecuteCreateTableDetailDto> details = new ArrayList<>();
        int successCount = 0;
        List<String> failedTables = new ArrayList<>();
        for (int i = 0; i < executeResult.length; i++) {
            boolean success = executeResult[i] == 0;
            if (success) {
                successCount++;
            } else {
                failedTables.add(tableNames.get(i));
            }
            details.add(new ExecuteCreateTableDetailDto()
                    .setSql(params.get(i).getRunSql())
                    .setTableName(tableNames.get(i))
                    .setSuccess(success)
                    .setMessage(success ? "执行成功" : "执行失败"));
        }
        if (CollectionUtil.isNotEmpty(failedTables)) {
            throw new CommonException(
                    ErrorCodeEnum.SERVICE_SQL_EXCUTE_ERROR_C0313,
                    "建表执行失败:" + String.join(SystemConstant.COMMA, failedTables));
        }
        return new ExecuteCreateTableResultDto()
                .setSuccess(true)
                .setTotalCount(createTableStatements.size())
                .setExecutedCount(successCount)
                .setDetails(details);
    }

    /**
     * 删除注释.
     *
     * @param sql sql脚本
     * @return 纯净版sql
     */
    private String cleanSqlStatement(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        Matcher singleLineMatcher = SINGLE_LINE_COMMENT_PATTERN.matcher(sql);
        return singleLineMatcher.replaceAll("");
    }

    /**
     * 获取 CREATE TABLE 脚本中的表名.
     *
     * @param sql sql脚本
     * @return 表名
     */
    private String getCreateTableName(String sql) {
        Matcher matcher = CREATE_TABLE_NAME_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return null;
        }
        String rawTableName = matcher.group(1);
        if (StrUtil.isBlank(rawTableName)) {
            return null;
        }
        String normalizedTableName = rawTableName.replace(SystemConstant.BACKTICK, "")
                .replace("\"", "");
        if (normalizedTableName.contains(SystemConstant.POINT)) {
            String[] tableNameItems = normalizedTableName.split("\\.");
            return tableNameItems[tableNameItems.length - 1];
        }
        return normalizedTableName;
    }

    /**
     * 判断目标库中表是否已存在.
     *
     * @param databaseService 元数据能力
     * @param dataSourceInfo  数据源信息
     * @param tableName       表名
     * @return 是否存在
     */
    private boolean isTargetTableExists(MetaDataSupport databaseService, DataSourceInfo dataSourceInfo, String tableName) {
        MetaDataQuery query = new MetaDataQuery();
        query.setTableNames(Collections.singletonList(tableName));
        MetaDataInfo metaData = databaseService.getMetaData(dataSourceInfo, query);
        if (metaData == null) {
            return false;
        }
        if (CollectionUtil.isNotEmpty(metaData.getTables())) {
            boolean existsInTables = metaData.getTables().stream()
                    .map(TableInfo::getTableName)
                    .anyMatch(name -> StringUtils.equalsIgnoreCase(name, tableName));
            if (existsInTables) {
                return true;
            }
        }
        if (MapUtil.isNotEmpty(metaData.getColumns())) {
            return metaData.getColumns().keySet().stream().anyMatch(name -> StringUtils.equalsIgnoreCase(name, tableName));
        }
        return false;
    }

    /**
     * 检查数据源是否支持建表.
     *
     * @param dsEntity 数据源
     */
    private void checkTableCreatable(DataSourceInfoEntity dsEntity) {
        String dbType = dsEntity.getDatabaseType();
        if (!StringUtils.equalsAny(dbType, DatabaseTypeEnum.HIVE.getType(), DatabaseTypeEnum.PAIMON.getType(),
                DatabaseTypeEnum.STARROCKS.getType(), DatabaseTypeEnum.MAXCOMPUTE.getType())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源类型不支持建表");
        }
    }

}

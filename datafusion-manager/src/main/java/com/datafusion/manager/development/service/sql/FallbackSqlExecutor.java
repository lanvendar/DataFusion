package com.datafusion.manager.development.service.sql;

import cn.hutool.core.collection.CollectionUtil;
import com.aliyun.odps.Column;
import com.aliyun.odps.data.Record;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 兜底SQL执行器, 适用于不支持取消/不暴露引擎实例的关系/大数据数据源.
 *
 * <p>逐段调用 {@link MetaDataSupport#execSql}, 不支持 stopInstance.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Slf4j
@Component
public class FallbackSqlExecutor implements DevSqlExecutor {

    /**
     * 单段SQL最多保留的预览行数.
     */
    private static final int MAX_PREVIEW_ROWS = 1000;

    @Override
    public boolean supports(DataSourceInfoEntity dsEntity) {
        return true;
    }

    @Override
    public void execute(DataSourceInfoEntity dsEntity,
                        List<String> sqls,
                        SqlExecutionCallback callback,
                        BooleanSupplier cancelled) {
        if (CollectionUtil.isEmpty(sqls)) {
            return;
        }
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dsEntity);
        TransformSupport transform = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo info = transform.transformDataSourceInfo(dsEntity);

        for (int i = 0; i < sqls.size(); i++) {
            if (cancelled.getAsBoolean()) {
                callback.onStatementCancelled(i, 0L);
                continue;
            }
            String sql = sqls.get(i);
            long startMs = System.currentTimeMillis();
            callback.onStatementStart(i);
            callback.onLog("INFO", String.format("第%d段SQL执行中: %s", i + 1, abbreviate(sql)));
            try {
                List<Record> records = databaseService.execSql(info, Collections.singletonList(new RunSqlParam(null, sql)));
                long cost = System.currentTimeMillis() - startMs;
                ExecSqlResultDto result = convertRecords(records);
                callback.onLog("INFO", String.format("第%d段SQL执行完成, 耗时%dms, 行数=%s",
                        i + 1, cost, result == null ? 0 : result.getRowCount()));
                callback.onStatementSuccess(i, cost, result);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - startMs;
                if (cancelled.getAsBoolean()) {
                    callback.onLog("WARN", String.format("第%d段SQL已取消", i + 1));
                    callback.onStatementCancelled(i, cost);
                    return;
                }
                callback.onLog("ERROR", String.format("第%d段SQL执行失败: %s", i + 1, e.getMessage()));
                callback.onStatementFailure(i, cost, e.getMessage());
                throw new RuntimeException("执行SQL失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 把 Record 列表转换为通用结果, 超过上限时只保留前若干行.
     *
     * @param records 记录
     * @return 结果集
     */
    private ExecSqlResultDto convertRecords(List<Record> records) {
        if (CollectionUtil.isEmpty(records)) {
            return null;
        }
        Column[] columns = records.get(0).getColumns();
        if (columns == null || columns.length == 0) {
            return null;
        }
        List<String> columnNames = Arrays.stream(columns).map(Column::getName).collect(Collectors.toList());
        int total = records.size();
        int previewSize = Math.min(total, MAX_PREVIEW_ROWS);
        List<Map<String, Object>> rows = new ArrayList<>(previewSize);
        for (int i = 0; i < previewSize; i++) {
            Record record = records.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.length; c++) {
                row.put(columnNames.get(c), record.get(c));
            }
            rows.add(row);
        }
        ExecSqlResultDto dto = new ExecSqlResultDto();
        dto.setColumns(columnNames);
        dto.setRows(rows);
        dto.setRowCount(total);
        if (total > MAX_PREVIEW_ROWS) {
            dto.setMessage(String.format("执行成功, 已截断展示前%d行(共%d行)", MAX_PREVIEW_ROWS, total));
        } else {
            dto.setMessage("执行成功");
        }
        return dto;
    }

    /**
     * 截断长SQL用于日志.
     *
     * @param sql SQL文本
     * @return 截断后的字符串
     */
    private String abbreviate(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}

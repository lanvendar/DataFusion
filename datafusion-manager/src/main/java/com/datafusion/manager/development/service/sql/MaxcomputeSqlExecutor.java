package com.datafusion.manager.development.service.sql;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.odps.Column;
import com.aliyun.odps.Instance;
import com.aliyun.odps.Odps;
import com.aliyun.odps.OdpsException;
import com.aliyun.odps.account.Account;
import com.aliyun.odps.account.AliyunAccount;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.task.SQLTask;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.TransformSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * MaxCompute 数据源的开发侧SQL执行器.
 *
 * <p>每段SQL单独提交一个 Instance, 暴露 instanceId 与日志事件; 取消时调用 instance.stop().</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Slf4j
@Component
public class MaxcomputeSqlExecutor implements DevSqlExecutor {

    /**
     * 单段SQL最多保留的预览行数.
     */
    private static final int MAX_PREVIEW_ROWS = 1000;

    @Override
    public boolean supports(DataSourceInfoEntity dsEntity) {
        return DatabaseTypeEnum.MAXCOMPUTE.getType().equals(dsEntity.getDatabaseType());
    }

    @Override
    public void execute(DataSourceInfoEntity dsEntity,
                        List<String> sqls,
                        SqlExecutionCallback callback,
                        BooleanSupplier cancelled) {
        if (CollectionUtil.isEmpty(sqls)) {
            return;
        }
        Odps odps = createClient(dsEntity);
        for (int i = 0; i < sqls.size(); i++) {
            if (cancelled.getAsBoolean()) {
                callback.onStatementCancelled(i, 0L);
                continue;
            }
            String sql = sqls.get(i);
            if (StrUtil.isBlank(sql)) {
                callback.onStatementSuccess(i, 0L, null);
                continue;
            }
            executeSingle(odps, i, sql, callback, cancelled);
        }
    }

    @Override
    public void stopInstance(DataSourceInfoEntity dsEntity, String instanceId) {
        if (StrUtil.isBlank(instanceId)) {
            return;
        }
        try {
            Odps odps = createClient(dsEntity);
            Instance instance = odps.instances().get(instanceId);
            instance.stop();
            log.info("MaxCompute Instance 已请求停止, instanceId={}", instanceId);
        } catch (OdpsException e) {
            log.warn("停止 MaxCompute Instance 失败 instanceId={}, msg={}", instanceId, e.getMessage());
        }
    }

    /**
     * 执行单段SQL.
     *
     * @param odps      Odps 客户端
     * @param index     语句索引
     * @param sql       SQL文本
     * @param callback  事件回调
     * @param cancelled 取消信号
     */
    private void executeSingle(Odps odps, int index, String sql,
                               SqlExecutionCallback callback, BooleanSupplier cancelled) {
        long startMs = System.currentTimeMillis();
        callback.onStatementStart(index);
        callback.onLog("INFO", String.format("第%d段SQL提交中: %s", index + 1, abbreviate(sql)));
        Instance instance;
        try {
            instance = SQLTask.run(odps, sql);
        } catch (OdpsException e) {
            long cost = System.currentTimeMillis() - startMs;
            callback.onLog("ERROR", String.format("第%d段SQL提交失败: %s", index + 1, e.getMessage()));
            callback.onStatementFailure(index, cost, e.getMessage());
            throw new RuntimeException("提交MaxCompute SQL失败: " + e.getMessage(), e);
        }
        String instanceId = instance.getId();
        callback.onInstanceId(index, instanceId);
        callback.onLog("INFO", String.format("第%d段SQL已提交, InstanceId=%s, 等待执行...", index + 1, instanceId));

        try {
            instance.waitForSuccess();
        } catch (OdpsException e) {
            long cost = System.currentTimeMillis() - startMs;
            if (cancelled.getAsBoolean()) {
                callback.onLog("WARN", String.format("第%d段SQL已取消, InstanceId=%s", index + 1, instanceId));
                callback.onStatementCancelled(index, cost);
                return;
            }
            callback.onLog("ERROR", String.format("第%d段SQL执行失败, InstanceId=%s, msg=%s",
                    index + 1, instanceId, e.getMessage()));
            callback.onStatementFailure(index, cost, e.getMessage());
            throw new RuntimeException("执行MaxCompute SQL失败: " + e.getMessage(), e);
        }

        long cost = System.currentTimeMillis() - startMs;
        ExecSqlResultDto result = null;
        try {
            List<Record> records = SQLTask.getResult(instance);
            result = convertRecords(records);
            callback.onLog("INFO", String.format("第%d段SQL执行完成, InstanceId=%s, 耗时%dms, 行数=%s",
                    index + 1, instanceId, cost, result == null ? 0 : result.getRowCount()));
        } catch (OdpsException e) {
            callback.onLog("WARN", String.format("第%d段SQL结果获取失败(可能为DDL/DML): %s", index + 1, e.getMessage()));
        }
        callback.onStatementSuccess(index, cost, result);
    }

    /**
     * 创建 Odps 客户端.
     *
     * @param dsEntity 数据源
     * @return Odps 客户端
     */
    private Odps createClient(DataSourceInfoEntity dsEntity) {
        TransformSupport transform = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo info = transform.transformDataSourceInfo(dsEntity);
        Account account = new AliyunAccount(info.getUsername(), info.getPassword());
        Odps odps = new Odps(account);
        if (info.getExtendParam() != null && info.getExtendParam().get("endpoint") != null) {
            odps.setEndpoint(info.getExtendParam().get("endpoint").toString());
        }
        odps.setDefaultProject(info.getDatabaseName());
        return odps;
    }

    /**
     * 把 ODPS Record 列表转换成 ExecSqlResultDto, 超过预览上限时仅保留前若干行.
     *
     * @param records ODPS 记录
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
     * @param sql sql文本
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

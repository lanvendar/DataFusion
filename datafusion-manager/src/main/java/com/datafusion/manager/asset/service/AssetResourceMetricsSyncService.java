package com.datafusion.manager.asset.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.manager.asset.dao.MetricsSyncRecordMapper;
import com.datafusion.manager.asset.dao.MetricsTmpMapper;
import com.datafusion.manager.asset.dto.MetricColumnInfo;
import com.datafusion.manager.asset.dto.MetricInfoDto;
import com.datafusion.manager.asset.po.MetricInfoEntity;
import com.datafusion.manager.asset.po.MetricSyncRecordEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 指标资源服务实现类.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceMetricsSyncService {

    /**
     * 同步状态：进行中.
     */
    private static final int SYNC_STATUS_RUNNING = 0;

    /**
     * 同步状态：成功.
     */
    private static final int SYNC_STATUS_SUCCESS = 1;

    /**
     * 同步状态：部分成功.
     */
    private static final int SYNC_STATUS_PARTIAL = 2;

    /**
     * 同步状态：失败.
     */
    private static final int SYNC_STATUS_FAILED = 3;

    /**
     * 同步类型：按日期.
     */
    private static final String SYNC_TYPE_DATE = "DATE";

    /**
     * 同步类型：按编码.
     */
    private static final String SYNC_TYPE_CODE = "CODE";

    /**
     * 同步类型：批量.
     */
    private static final String SYNC_TYPE_BATCH = "BATCH";

    /**
     * 分批大小.
     */
    private static final int BATCH_SIZE = 20;

    /**
     * 指标信息Mapper.
     */
    private final MetricsTmpMapper metricsTmpMapper;

    /**
     * 同步记录Mapper.
     */
    private final MetricsSyncRecordMapper syncRecordMapper;

    /**
     * 资源指标服务.
     */
    private final AssetResourceMetricsService assetResourceMetricsService;

    /**
     * 从数据库同步指标数据到血缘数据库.
     *
     * @param afterDate      同步指定日期后的指标.
     * @param metricCode     指标编码（可选），为空则同步所有指标.
     * @param thirdMetricIds 外部指标ID列表（可选），用于批量同步.
     */
    public void syncMetricsFromDb(LocalDate afterDate, String metricCode, List<String> thirdMetricIds) {
        // 确定同步类型.
        String syncType = determineSyncType(afterDate, metricCode, thirdMetricIds);
        String syncParam = buildSyncParam(afterDate, metricCode, thirdMetricIds);

        // 创建同步记录.
        UUID recordId = createSyncRecord(syncType, syncParam);

        try {
            List<MetricInfoEntity> metricList = fetchMetricList(afterDate, metricCode, thirdMetricIds);

            if (CollectionUtil.isEmpty(metricList)) {
                log.info("没有需要同步的指标数据");
                updateSyncRecord(recordId, SYNC_STATUS_SUCCESS, 0, 0, null);
                return;
            }

            log.info("查询到 {} 条指标数据需要同步", metricList.size());

            // 将MetricInfoEntity转换为MetricInfoDTO
            List<MetricInfoDto> metricDTOList = metricList.stream()
                    .map(metric -> {
                        MetricInfoDto dto = new MetricInfoDto();
                        BeanUtil.copyProperties(metric, dto);
                        return dto;
                    })
                    .collect(Collectors.toList());

            // [V2] 处理apiUrl（过滤中文、规范化路径）
            metricDTOList = processApiUrl(metricDTOList);

            // [V2] 处理表名（过滤dw.前缀）
            metricDTOList = processTableName(metricDTOList);

            // [V2] 按 metricCode + apiUrl 分组处理
            List<MetricInfoDto> groupedMetricList = groupByMetricCode(metricDTOList);

            // 分批处理
            int totalSuccess = 0;
            List<String> allFailMetricIds = new ArrayList<>();
            List<List<MetricInfoDto>> batches = Lists.partition(groupedMetricList, BATCH_SIZE);
            log.info("共 {} 条指标数据，分为 {} 批处理", groupedMetricList.size(), batches.size());

            for (int i = 0; i < batches.size(); i++) {
                List<MetricInfoDto> batch = batches.get(i);
                log.info("处理第 {} 批，共 {} 条指标", i + 1, batch.size());

                MetricSyncRecordEntity batchResult = assetResourceMetricsService.addMetrics(batch);
                totalSuccess += batchResult.getSuccessCount();

                // 累加失败的metricId
                if (StrUtil.isNotBlank(batchResult.getFailMetricIds())) {
                    allFailMetricIds.addAll(
                            Arrays.stream(batchResult.getFailMetricIds().split(","))
                                    .map(String::trim)
                                    .filter(StrUtil::isNotBlank)
                                    .collect(Collectors.toList()));
                }
            }

            int failCount = allFailMetricIds.size();
            int syncStatus = (failCount > 0) ? SYNC_STATUS_PARTIAL : SYNC_STATUS_SUCCESS;
            updateSyncRecord(recordId, syncStatus, groupedMetricList.size(), totalSuccess, allFailMetricIds);

            log.info("指标数据同步完成, 成功: {}, 失败: {}", totalSuccess, failCount);

        } catch (Exception e) {
            log.error("同步指标数据异常", e);
            updateSyncRecord(recordId, SYNC_STATUS_FAILED, 0, 0, null);
            throw e;
        }
    }

    /**
     * 确定同步类型.
     *
     * @param afterDate      同步指定日期后的指标.
     * @param metricCode     指标编码（可选）.
     * @param thirdMetricIds 外部指标ID列表（可选）.
     * @return 同步类型.
     */
    private String determineSyncType(LocalDate afterDate, String metricCode, List<String> thirdMetricIds) {
        if (CollectionUtil.isNotEmpty(thirdMetricIds)) {
            return SYNC_TYPE_BATCH;
        }
        if (StrUtil.isNotBlank(metricCode)) {
            return SYNC_TYPE_CODE;
        }
        return SYNC_TYPE_DATE;
    }

    /**
     * 构建同步参数.
     *
     * @param afterDate      同步指定日期后的指标.
     * @param metricCode     指标编码（可选）.
     * @param thirdMetricIds 外部指标ID列表（可选）.
     * @return 同步参数JSON.
     */
    private String buildSyncParam(LocalDate afterDate, String metricCode, List<String> thirdMetricIds) {
        StringBuilder sb = new StringBuilder();
        if (afterDate != null) {
            sb.append("afterDate:").append(afterDate.format(DateTimeFormatter.ISO_DATE)).append(",");
        }
        if (StrUtil.isNotBlank(metricCode)) {
            sb.append("metricCode:").append(metricCode).append(",");
        }
        if (CollectionUtil.isNotEmpty(thirdMetricIds)) {
            sb.append("thirdMetricIds:").append(String.join(",", thirdMetricIds));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 创建同步记录.
     *
     * @param syncType  同步类型.
     * @param syncParam 同步参数.
     * @return 同步记录ID.
     */
    private UUID createSyncRecord(String syncType, String syncParam) {
        MetricSyncRecordEntity record = new MetricSyncRecordEntity();
        record.setId(UUID.randomUUID());
        record.setSyncType(syncType);
        record.setSyncParam(syncParam);
        record.setSyncStatus(SYNC_STATUS_RUNNING);
        record.setSyncStartTime(new Date());
        record.setCreator(HttpUtils.getCurrentUserName());
        record.setCreateTime(new Date());
        syncRecordMapper.insert(record);
        return record.getId();
    }

    /**
     * 获取指标数据列表.
     * 流程：1.清空临时表 2.写入原始数据 3.从临时表查询
     *
     * @param afterDate      同步指定日期后的指标.
     * @param metricCode     指标编码（可选）.
     * @param thirdMetricIds 外部指标ID列表（可选）.
     * @return 指标数据列表.
     */
    private List<MetricInfoEntity> fetchMetricList(LocalDate afterDate, String metricCode, List<String> thirdMetricIds) {
        // 1. 清空临时表
        metricsTmpMapper.truncateMetricTmpTable();

        // 2. 将原始查询结果写入临时表（不过滤条件）
        metricsTmpMapper.insertMetricTmpFromOriginal();

        // 3. 从临时表查询数据
        return metricsTmpMapper.selectMetricTmpList(afterDate, metricCode, thirdMetricIds);
    }

    /**
     * 更新同步记录.
     *
     * @param recordId      同步记录ID.
     * @param syncStatus    同步状态.
     * @param totalCount    总数.
     * @param successCount  成功数.
     * @param failMetricIds 失败的thirdMetricId列表.
     */
    private void updateSyncRecord(UUID recordId, int syncStatus, int totalCount, int successCount, List<String> failMetricIds) {
        String failMetricIdStr = null;
        int failCount = 0;
        if (CollectionUtil.isNotEmpty(failMetricIds)) {
            failMetricIdStr = failMetricIds.stream().collect(Collectors.joining(","));
            failCount = failMetricIds.size();
        }

        syncRecordMapper.updateSyncStatus(
                recordId,
                syncStatus,
                new Date(),
                totalCount,
                successCount,
                failCount,
                failMetricIdStr);
    }

    /**
     * 处理apiUrl路径规范化.
     * 规则：
     * 1. 如果包含中文，丢弃（返回空列表）
     * 2. 如果是 /api/openapi/custom/station-month-data 或以 /api/openapi/ 开头，保持不变
     * 3. 如果以 /custom/ 开头，拼接上 /api/openapi
     * 4. 其他情况（不包含 /api/openapi/ 和 /custom/），拼接上 /api/openapi/custom/
     *
     * @param metricList 原始指标列表
     * @return 处理后的指标列表
     */
    private List<MetricInfoDto> processApiUrl(List<MetricInfoDto> metricList) {
        if (CollectionUtil.isEmpty(metricList)) {
            return metricList;
        }

        List<MetricInfoDto> result = new ArrayList<>();
        for (MetricInfoDto metric : metricList) {
            String apiUrl = metric.getApiUrl();
            if (StrUtil.isBlank(apiUrl)) {
                //apiUrl 为空可以丢弃
                //result.add(metric);
                continue;
            }

            // 1. 如果包含中文，丢弃
            if (containsChinese(apiUrl)) {
                log.info("apiUrl包含中文，跳过: {}", apiUrl);
                continue;
            }

            // 2. 如果是 /api/openapi/custom/station-month-data 或以 /api/openapi/ 开头，保持不变
            if (apiUrl.startsWith("/api/openapi/") || apiUrl.equals("/api/openapi/custom/station-month-data")) {
                metric.setApiUrl(apiUrl);
                result.add(metric);
                continue;
            }

            // 3. 如果以 /custom/ 开头，拼接上 /api/openapi
            if (apiUrl.startsWith("/custom/")) {
                metric.setApiUrl("/api/openapi" + apiUrl);
                result.add(metric);
                continue;
            }

            // 4. 其他情况，拼接上 /api/openapi/custom/
            metric.setApiUrl("/api/openapi/custom/" + apiUrl.replaceFirst("^/", ""));
            result.add(metric);
        }

        log.info("apiUrl处理完成: 原始数量={}, 处理后={}", metricList.size(), result.size());
        return result;
    }

    /**
     * 处理表名，过滤dw.前缀.
     * 规则：如果表名包含 "dw."，则过滤掉（移除 dw. 前缀）
     * 同时处理 columnInfoList 中的表名
     *
     * @param metricList 原始指标列表
     * @return 处理后的指标列表
     */
    private List<MetricInfoDto> processTableName(List<MetricInfoDto> metricList) {
        if (CollectionUtil.isEmpty(metricList)) {
            return metricList;
        }

        for (MetricInfoDto metric : metricList) {
            // 处理 tableName
            String tableName = metric.getTableName();
            if (StrUtil.isNotBlank(tableName) && tableName.startsWith("dw.")) {
                metric.setTableName(tableName.substring(3));
            }

            // 处理 columnInfoList 中的表名
            List<MetricColumnInfo> columnInfoList = metric.getColumnInfoList();
            if (CollectionUtil.isNotEmpty(columnInfoList)) {
                for (MetricColumnInfo columnInfo : columnInfoList) {
                    String colTableName = columnInfo.getTableName();
                    if (StrUtil.isNotBlank(colTableName) && colTableName.startsWith("dw.")) {
                        columnInfo.setTableName(colTableName.substring(3));
                    }
                }
            }
        }

        log.info("表名处理完成: 过滤dw.前缀");
        return metricList;
    }

    /**
     * 判断字符串是否包含中文.
     *
     * @param str 待检查的字符串
     * @return true-包含中文，false-不包含
     */
    private boolean containsChinese(String str) {
        if (StrUtil.isBlank(str)) {
            return false;
        }
        // Unicode范围中文是 \u4e00-\u9fa5
        for (char c : str.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 metricCode + apiUrl 分组处理.
     * 将相同 metricCode 的记录合并为一条，columnInfoList 包含该指标对应的所有字段信息.
     *
     * @param metricList 原始指标列表
     * @return 分组后的指标列表
     */
    public List<MetricInfoDto> groupByMetricCode(List<MetricInfoDto> metricList) {
        if (CollectionUtil.isEmpty(metricList)) {
            return metricList;
        }

        // 按 metricCode + apiUrl 分组
        Map<String, List<MetricInfoDto>> groupedByCode = metricList.stream()
                .collect(Collectors.groupingBy(m -> m.getDimension() + "|" + m.getMetricCode() + "|" + StrUtil.emptyToNull(m.getApiUrl())));

        // 转换为分组后的DTO列表
        List<MetricInfoDto> groupedMetricList = new ArrayList<>();
        for (Map.Entry<String, List<MetricInfoDto>> entry : groupedByCode.entrySet()) {
            List<MetricInfoDto> group = entry.getValue();
            MetricInfoDto representative = group.get(0);

            // 构建 columnInfoList（从分组的所有元素中提取）
            List<MetricColumnInfo> columnInfoList = group.stream()
                    .map(m -> {
                        MetricColumnInfo info = new MetricColumnInfo();
                        info.setColumnName(m.getColumnName());
                        info.setTableName(m.getTableName());
                        info.setDatasourceName(m.getDatasourceName());
                        return info;
                    })
                    .collect(Collectors.toList());

            representative.setColumnInfoList(columnInfoList);
            groupedMetricList.add(representative);
        }

        log.info("按metricCode+apiUrl分组完成: 原始数量={}, 分组后={}",
                metricList.size(), groupedMetricList.size());

        return groupedMetricList;
    }

}

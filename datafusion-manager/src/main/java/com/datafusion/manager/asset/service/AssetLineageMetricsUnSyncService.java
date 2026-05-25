package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.manager.asset.config.ResourceSyncConfig;
import com.datafusion.manager.asset.dao.AssetLineageMetricsUnTmpMapper;
import com.datafusion.manager.asset.dao.TagAccessStatsMapper;
import com.datafusion.manager.asset.dto.MetricInfoDto;
import com.datafusion.manager.asset.po.AssetLineageMetricsUnTmpEntity;
import com.datafusion.manager.asset.po.MetricSyncRecordEntity;
import com.datafusion.manager.asset.po.TagAccessStatsEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一指标同步服务.
 * 从 tag_access_stats 获取 API 信息，通过 uri 解析 physical_level 和 timeliness，
 * 与 tag_info 匹配后同步到 asset_lineage_metrics_un_tmp 表，再写入资源表.
 *
 * @author feng.xu
 * @version 1.0.0 , 2026/04/16
 * @since 2026/04/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLineageMetricsUnSyncService {

    /**
     * 统一指标 Mapper.
     */
    private final AssetLineageMetricsUnTmpMapper assetLineageMetricsUnTmpMapper;

    /**
     * tag_access_stats Mapper.
     */
    private final TagAccessStatsMapper tagAccessStatsMapper;

    /**
     * 资源指标服务.
     */
    private final AssetResourceMetricsService assetResourceMetricsService;

    /**
     * 资源配置.
     */
    private final ResourceSyncConfig resourceSyncConfig;

    /**
     * 分批大小.
     */
    private static final int BATCH_SIZE = 20;

    /**
     * 同步统一指标数据.
     * 流程：
     * 1. 遍历 tag_access_stats
     * 2. 用 uri 解析 physicalLevel + timeliness
     * 3. dimension 转换
     * 4. 匹配 tag_info (tag + physical_level + timeliness)
     * 5. 拆分 time_dimension (逗号分隔)
     * 6. 生成主键 (tag_info.id + time_dimension)
     * 7. 处理 apiUrl
     * 8. 写入 asset_lineage_metrics_un_tmp
     * 9. 查询临时表 + 处理apiUrl + 分组
     * 10. 调用 addMetrics 写入资源表
     */
    public void syncUnifiedMetrics() {
        log.info("开始同步统一指标数据");

        // 1. 清空临时表
        assetLineageMetricsUnTmpMapper.truncateTable();
        log.info("清空临时表 asset_lineage_metrics_un_tmp 完成");

        // 2. 查询 tag_access_stats
        List<TagAccessStatsEntity> accessStatsList = tagAccessStatsMapper.selectAll();
        if (CollectionUtil.isEmpty(accessStatsList)) {
            log.info("tag_access_stats 没有数据");
            return;
        }
        log.info("查询到 {} 条 tag_access_stats 数据", accessStatsList.size());

        // 3. 查询 tag_info
        List<Map<String, Object>> tagInfoList = assetLineageMetricsUnTmpMapper.selectTagInfoList();
        if (CollectionUtil.isEmpty(tagInfoList)) {
            log.info("tag_info 没有数据");
            return;
        }
        log.info("查询到 {} 条 tag_info 数据", tagInfoList.size());

        Map<String, Map<String, Object>> tagInfoMap = tagInfoList.stream()
                .flatMap(info -> {
                    // 1. 获取 time_dimension 字符串，例如 "day,month,year"
                    String timeDimStr = String.valueOf(info.getOrDefault("time_dimension", ""));
                    if (timeDimStr.isEmpty()) {
                        return Stream.empty();
                    }

                    // 2. 按逗号拆分
                    return Arrays.stream(timeDimStr.split(","))
                            .map(String::trim) // 去空格
                            .filter(dim -> !dim.isEmpty()) // 过滤空串
                            .map(dim -> {
                                // 3. 构造 Key: 拆分后的维度 + 原有的其它字段
                                String key = dim + "|"
                                        + info.get("tag_code") + "|"
                                        + info.get("physical_level") + "|"
                                        + info.get("timeliness");
                                // 返回一个临时的键值对对象
                                return new AbstractMap.SimpleEntry<>(key, info);
                            });
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1 // 如果 key 重复，保留第一个
                ));

        // 4. 处理每条 access_stats 数据
        List<AssetLineageMetricsUnTmpEntity> resultList = new ArrayList<>();
        Date now = new Date();
        String creator = HttpUtils.getCurrentUserName();

        for (TagAccessStatsEntity accessStats : accessStatsList) {
            // 解析 uri 获取 physical_level 和 timeliness
            String[] parsed = parseUri(accessStats.getUri());
            String physicalLevel = parsed[0];
            String timeliness = parsed[1];

            if (StrUtil.isBlank(physicalLevel) || StrUtil.isBlank(timeliness)) {
                log.info("URI解析失败，跳过: {}", accessStats.getUri());
                continue;
            }

            // dimension 转换
            String convertedDimension = convertDimension(accessStats.getDimension());
            if (StrUtil.isBlank(convertedDimension)) {
                log.info("dimension转换失败，跳过: {}", accessStats.getDimension());
                continue;
            }

            // 匹配 tag_info
            String matchKey = convertedDimension + "|" + accessStats.getTag() + "|" + physicalLevel + "|" + timeliness;
            Map<String, Object> tagInfo = tagInfoMap.get(matchKey);

            if (tagInfo == null) {
                log.info("未匹配到 tag_info，跳过: tag={}, physicalLevel={}, timeliness={}",
                        accessStats.getTag(), physicalLevel, timeliness);
                continue;
            }

            // 处理 apiUrl
            String apiUrl = processApiUrl(accessStats.getUri());
            if (StrUtil.isBlank(apiUrl)) {
                log.info("apiUrl处理失败，跳过: {}", accessStats.getUri());
                continue;
            }

            // 获取 tag_info 字段
            Long tagInfoId = ((Number) tagInfo.get("id")).longValue();
            String tagName = (String) tagInfo.get("tag_name");
            String tagCode = (String) tagInfo.get("tag_code");
            String timeDimension = (String) tagInfo.get("time_dimension");

            // 拆分 time_dimension (逗号分隔)
            if (StrUtil.isBlank(timeDimension)) {
                log.info("time_dimension 为空，跳过: tagInfoId={}", tagInfoId);
                continue;
            }

            String[] dimensions = timeDimension.split(",");
            for (String dim : dimensions) {
                dim = dim.trim();
                if (StrUtil.isBlank(dim)) {
                    continue;
                }

                // 生成主键: tag_info.id + "_" + dimension
                String id = tagInfoId + "_" + dim;

                // 构建实体
                AssetLineageMetricsUnTmpEntity entity = new AssetLineageMetricsUnTmpEntity();
                entity.setId(id);
                entity.setMetricName(tagName);
                entity.setMetricCode(tagCode);
                entity.setDimension(dim);
                entity.setPhysicalLevel(physicalLevel);
                entity.setTimeliness(timeliness);
                entity.setApiUrl(apiUrl);
                entity.setCreator(creator);
                entity.setCreateTime(now);
                entity.setUpdater(creator);
                entity.setUpdateTime(now);

                resultList.add(entity);
            }
        }

        if (CollectionUtil.isEmpty(resultList)) {
            log.info("没有需要同步的统一指标数据");
            return;
        }

        // 按主键去重
        List<AssetLineageMetricsUnTmpEntity> distinctList = resultList.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new java.util.TreeSet<>(Comparator.comparing(AssetLineageMetricsUnTmpEntity::getId))),
                        ArrayList::new));

        log.info("共 {} 条统一指标数据，去重后 {} 条", resultList.size(), distinctList.size());

        // 5. 批量插入临时表
        // 分批处理，每批 1000 条
        int tmpBatchSize = 1000;
        List<List<AssetLineageMetricsUnTmpEntity>> tmpBatches = partitionList(distinctList, tmpBatchSize);

        for (int i = 0; i < tmpBatches.size(); i++) {
            List<AssetLineageMetricsUnTmpEntity> batch = tmpBatches.get(i);
            int count = assetLineageMetricsUnTmpMapper.batchInsert(batch);
            log.info("第 {} 批插入临时表完成，插入 {} 条数据", i + 1, count);
        }

        log.info("临时表写入完成");

        // 6. 查询临时表数据，转换为 MetricInfoDto
        List<AssetLineageMetricsUnTmpEntity> tmpDataList = assetLineageMetricsUnTmpMapper.selectAll();
        if (CollectionUtil.isEmpty(tmpDataList)) {
            log.info("临时表没有数据");
            return;
        }

        // 7. 转换为 MetricInfoDto 列表
        List<MetricInfoDto> metricDTOList = tmpDataList.stream()
                .map(tmp -> {
                    MetricInfoDto dto = new MetricInfoDto();
                    dto.setThirdMetricId(tmp.getId());
                    dto.setMetricCode(tmp.getMetricCode());
                    dto.setMetricName(tmp.getMetricName());
                    dto.setDimension(tmp.getDimension());
                    dto.setPhysicalLevel(tmp.getPhysicalLevel());
                    dto.setTimeliness(tmp.getTimeliness());
                    dto.setApiUrl(tmp.getApiUrl());
                    dto.setType("un"); // 统一指标
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("转换为 MetricInfoDto 完成，共 {} 条", metricDTOList.size());

        // 9. 调用 addMetrics 写入资源表
        int totalSuccess = 0;
        List<String> allFailMetricIds = new ArrayList<>();
        List<List<MetricInfoDto>> batches = Lists.partition(metricDTOList, BATCH_SIZE);
        log.info("共 {} 条指标数据，分为 {} 批处理", metricDTOList.size(), batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<MetricInfoDto> batch = batches.get(i);
            log.info("处理第 {} 批，共 {} 条指标", i + 1, batch.size());

            MetricSyncRecordEntity batchResult = assetResourceMetricsService.addMetrics(batch);
            totalSuccess += batchResult.getSuccessCount();

            // 累加失败的 metricId
            if (StrUtil.isNotBlank(batchResult.getFailMetricIds())) {
                allFailMetricIds.addAll(
                        Arrays.stream(batchResult.getFailMetricIds().split(","))
                                .map(String::trim)
                                .filter(StrUtil::isNotBlank)
                                .collect(Collectors.toList()));
            }
        }

        log.info("统一指标数据同步完成, 成功: {}, 失败: {}", totalSuccess, allFailMetricIds.size());
    }

    /**
     * 通过 timeDimensionMap 转换 dimension.
     *
     * @param dimension 原始 dimension
     * @return 转换后的 dimension
     */
    private String convertDimension(String dimension) {
        if (dimension == null) {
            return null;
        }
        return resourceSyncConfig.getTimeDimensionMap().getOrDefault(dimension, dimension);
    }

    /**
     * 从 uri 解析 physicalLevel 和 timeliness.
     *
     * @param uri URI
     * @return 数组 [physicalLevel, timeliness]
     */
    private String[] parseUri(String uri) {
        String[] result = new String[2];
        if (uri == null) {
            return result;
        }

        // 解析 timeliness: /current -> t0, 否则 -> t1
        result[1] = uri.contains("/current") ? "t0" : "t1";

        // 解析 physicalLevel: 提取 /unified_metrics/ 后的路径段
        // 如 /api/biz-data/unified_metrics/nodes/query/current -> nodes -> node
        // 如 /api/biz-data/unified_metrics/stations/query/current/agg -> stations + agg -> station_aggregate
        if (uri.contains("/unified_metrics/")) {
            String[] parts = uri.split("/");
            List<String> unifiedMetricsParts = new ArrayList<>();
            boolean foundUnifiedMetrics = false;

            for (String part : parts) {
                if ("unified_metrics".equals(part)) {
                    foundUnifiedMetrics = true;
                    continue;
                }
                if (foundUnifiedMetrics) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    unifiedMetricsParts.add(part);
                }
            }

            if (!unifiedMetricsParts.isEmpty()) {
                // 获取第一段（如 nodes, stations）
                String path = unifiedMetricsParts.get(0);
                if (path.equals("projects")) {
                    path = "system";
                }

                if (path.endsWith("s")) {
                    path = path.substring(0, path.length() - 1);
                }

                // 检查最后一段是否是 agg
                if (unifiedMetricsParts.size() > 1 && "agg".equals(unifiedMetricsParts.get(unifiedMetricsParts.size() - 1))) {
                    path = path + "_aggregate";
                }
                //检查最后一段是不是reading
                if (unifiedMetricsParts.size() > 1 && "reading".equals(unifiedMetricsParts.get(unifiedMetricsParts.size() - 1))) {
                    path = path + "_view";
                }

                // 去除复数 s
                //                if (path.endsWith("s") && !path.endsWith("_aggregate")) {
                //                    path = path.substring(0, path.length() - 1);
                //                }

                result[0] = path;
            }
        }

        return result;
    }

    /**
     * 处理 apiUrl 路径规范化.
     * 规则：
     * 1. 如果包含中文，返回 null (过滤掉)
     * 2. 如果是 /api/openapi/custom/station-month-data 或以 /api/openapi/ 开头，保持不变
     * 3. 如果以 /custom/ 开头，拼接上 /api/openapi
     * 4. 其他情况，拼接上 /api/openapi/custom/
     *
     * @param apiUrl 原始 URI
     * @return 规范化后的 apiUrl
     */
    private String processApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }

        // 1. 如果包含中文，返回 null (过滤掉)
        if (containsChinese(apiUrl)) {
            return null;
        }

        // 2. 处理连续两个 "/" 的情况（替换成单个 "/"）
        String result = apiUrl;
        while (result.contains("//")) {
            result = result.replace("//", "/");
        }

        // 3. 处理末尾多余的 "/"
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * 判断字符串是否包含中文.
     *
     * @param str 待检查的字符串
     * @return true-包含中文，false-不包含
     */
    private boolean containsChinese(String str) {
        if (str == null || str.isBlank()) {
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
     * 将相同 metricCode 的记录合并为一条（统一指标场景下主要是去重）.
     *
     * @param metricList 原始指标列表
     * @return 分组后的指标列表
     */
    private List<MetricInfoDto> groupByMetricCode(List<MetricInfoDto> metricList) {
        if (CollectionUtil.isEmpty(metricList)) {
            return metricList;
        }

        // 按 metricCode + apiUrl 分组
        Map<String, List<MetricInfoDto>> groupedByCode = metricList.stream()
                .collect(Collectors.groupingBy(m -> m.getMetricCode() + "|" + StrUtil.emptyToNull(m.getApiUrl())));

        // 转换为分组后的DTO列表（统一指标不需要合并columnInfoList）
        List<MetricInfoDto> groupedMetricList = new ArrayList<>();
        for (Map.Entry<String, List<MetricInfoDto>> entry : groupedByCode.entrySet()) {
            groupedMetricList.add(entry.getValue().get(0));
        }

        log.info("按metricCode+apiUrl分组完成: 原始数量={}, 分组后={}",
                metricList.size(), groupedMetricList.size());

        return groupedMetricList;
    }

    /**
     * 将列表分批.
     *
     * @param list      原始列表
     * @param batchSize 每批大小
     * @return 分批后的列表
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            result.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return result;
    }
}

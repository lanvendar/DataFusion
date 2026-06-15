package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon 写入前记录归一化.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RecordNormalizer {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordNormalizer.class);

    private RecordNormalizer() {
    }

    /**
     * 按字段顺序、默认值和 format 归一化记录.
     *
     * @param records 原始记录
     * @param tableConfig 目标表配置
     * @param recordErrorPolicy 单条记录错误处理策略
     * @return 归一化记录
     */
    public static List<PaimonRecord> normalize(List<PaimonRecord> records, ResolvedTableConfig tableConfig,
            RecordErrorPolicy recordErrorPolicy) {
        List<PaimonRecord> normalized = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            PaimonRecord normalizedRecord = normalizeRecord(records.get(i), tableConfig, recordErrorPolicy);
            if (normalizedRecord != null) {
                normalized.add(normalizedRecord);
            }
        }
        return normalized;
    }

    private static PaimonRecord normalizeRecord(PaimonRecord record, ResolvedTableConfig tableConfig,
            RecordErrorPolicy recordErrorPolicy) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (ColumnConfig column : tableConfig.columns) {
            try {
                normalized.put(column.name, normalizeValue(record.values, column));
            } catch (RuntimeException e) {
                return handleRecordError(tableConfig, record, column.name, recordErrorPolicy, e);
            }
        }
        return PaimonRecord.of(normalized, record.topic, record.partition, record.offset, record.recordIndex);
    }

    private static Object normalizeValue(Map<String, Object> record, ColumnConfig column) {
        Object value = record == null ? null : record.get(column.name);
        if (!isEmpty(value) && !TextUtils.isBlank(column.format)) {
            value = formatValue(column, String.valueOf(value));
        }
        return value;
    }

    private static PaimonRecord handleRecordError(ResolvedTableConfig tableConfig, PaimonRecord record, String columnName,
            RecordErrorPolicy recordErrorPolicy, RuntimeException e) {
        if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
            throw e;
        }
        LOGGER.warn("Skip Paimon record because record error, identifier={}, topic={}, partition={}, offset={}, column={}, "
                        + "recordIndex={}, reason={}",
                tableConfig.identifier(), record.topic, record.partition, record.offset, columnName, record.recordIndex, e.getMessage());
        return null;
    }

    private static Object formatValue(ColumnConfig column, String value) {
        String[] formats = column.format.split("->", 2);
        DateTimeFormatter source = DateTimeFormatter.ofPattern(formats[0]);
        DateTimeFormatter target = DateTimeFormatter.ofPattern(formats.length == 2 ? formats[1] : formats[0]);
        String type = TextUtils.upper(column.dataType, "STRING");
        if ("DATE".equals(type)) {
            try {
                return LocalDateTime.parse(value, source).toLocalDate().format(target);
            } catch (java.time.format.DateTimeParseException e) {
                return LocalDate.parse(value, source).format(target);
            }
        }
        return LocalDateTime.parse(value, source).format(target);
    }

    private static boolean isEmpty(Object value) {
        return value == null || value instanceof String text && TextUtils.isBlank(text);
    }
}

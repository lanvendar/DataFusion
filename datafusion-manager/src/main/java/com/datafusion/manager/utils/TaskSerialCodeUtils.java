package com.datafusion.manager.utils;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 任务类业务编码生成：{@code 类型前缀 + yyMMdd + 4位序号}（与数据集成任务一致规则）.
 *
 * <p>各业务表通过 Mapper 查询当日前缀下最大 {@code code}，再调用 {@link #nextSerialCode(String, String)} 递增。
 * 类型前缀按域区分（如集成 JC、开发 KF），避免不同表共用同一前缀时序号语义混淆。</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
public final class TaskSerialCodeUtils {

    /**
     * 日期段格式：yyMMdd.
     */
    public static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * 数据同步任务表 {@code ingestion_datasync_task.code} 使用的类型前缀.
     */
    public static final String PREFIX_INGESTION_DATASYNC_TASK = "JC";

    /**
     * SQL脚本任务表 {@code develop_script_sql_task.code} 使用的类型前缀（与数据结构文档示例一致）.
     */
    public static final String PREFIX_DEVELOP_SCRIPT_SQL_TASK = "KF";

    private TaskSerialCodeUtils() {
    }

    /**
     * 构造含日期的编码前缀：{@code typePrefix + yyMMdd}.
     *
     * @param typePrefix 类型前缀（如 {@link #PREFIX_INGESTION_DATASYNC_TASK}）
     * @param date       业务日（一般为当天）
     * @return 如 {@code JC250513}
     */
    public static String buildDatedPrefix(String typePrefix, LocalDate date) {
        return typePrefix + date.format(DATE_PART);
    }

    /**
     * 在指定「日期前缀」下生成下一个 4 位序号整码.
     *
     * @param datedPrefix 已含日期的前缀，如 {@code JC250513}
     * @param maxFullCode 库中该前缀下字典序最大的完整编码；若无传 {@code null} 或空串
     * @return 新编码，如 {@code JC2505130001}
     */
    public static String nextSerialCode(String datedPrefix, String maxFullCode) {
        int seq = 1;
        if (StrUtil.isNotBlank(maxFullCode) && maxFullCode.length() > datedPrefix.length()) {
            String seqStr = maxFullCode.substring(datedPrefix.length());
            seq = Integer.parseInt(seqStr) + 1;
        }
        return datedPrefix + String.format("%04d", seq);
    }
}

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

package com.datafusion.common.uuid;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;

import java.util.Date;
import java.util.UUID;

/**
 * UUID主键生成器.
 *
 * @author david
 * @version 3.6.4, 2024/8/22
 * @since 3.6.4, 2024/8/22
 */
public class IdGenerator {

    /**
     * 创建数据源ID.
     *
     * @param projectId 项目ID
     * @param name      数据源名称
     * @return 数据源UUID
     */
    public static UUID createDsId(UUID projectId, String name) {
        return Uuid5.fromUtf8(projectId, name);
    }

    /**
     * 创建数据源ID.
     *
     * @param name 数据源名称
     * @return 数据源UUID
     */
    public static UUID createDsId(String name) {
        return Uuid5.fromUtf8(name);
    }

    /**
     * 创建表ID.
     *
     * @param schemaId  数据源ID
     * @param tableName 表名
     * @return 元数据-表UUID
     */
    public static UUID createTableId(UUID schemaId, String tableName) {
        return Uuid5.fromUtf8(schemaId, tableName);
    }

    /**
     * 创建字段ID.
     *
     * @param tableId    表ID
     * @param columnName 字段名
     * @return 元数据-字段UUID
     */
    public static UUID createColumnId(UUID tableId, String columnName) {
        return Uuid5.fromUtf8(tableId, columnName);
    }

    /**
     * 创建任务定义ID.
     *
     * @param projectId 项目ID
     * @param code      任务编码
     * @return 任务定义ID
     */
    public static UUID createTaskDefId(UUID projectId, String code) {
        return Uuid5.fromUtf8(projectId, code);
    }

    /**
     * 创建事件实例ID.
     *
     * @param eventId    事件ID
     * @param effectTime 生效时间
     * @return 事件实例ID
     */
    public static UUID createEventInstanceId(UUID eventId, Long effectTime) {
        return Uuid5.fromUtf8(eventId, effectTime.toString());
    }
    
    /**
     * 生成轨迹id.
     * @param operateType 0:批量创建|1:批量对比
     * @return 轨迹id
     */
    public static UUID createTrackId(String operateType) {
        return Uuid5.fromUtf8(operateType, DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
    }
}

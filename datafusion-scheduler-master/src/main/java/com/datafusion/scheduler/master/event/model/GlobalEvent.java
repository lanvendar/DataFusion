/*
 * Copyright © 2020-2022 Nimbus Corporation All rights reserved.
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

package com.datafusion.scheduler.master.event.model;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.TimeAlignmentEnum;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import lombok.Data;

/**
 * 全局业务事件.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/6
 * @since 2022/6/6
 */
@Data
public class GlobalEvent {

    /**
     * 事件id.
     */
    private String id;

    /**
     * 事件类型 1：task 2：flow.
     */
    private EventTypeEnum type;

    /**
     * flow instance id.
     */
    private String flowInstanceId;

    /**
     * task instance id.
     */
    private String taskInstanceId;

    /**
     * 业务时间.
     */
    private Long eventTime;

    /**
     * 业务时间对齐格式.
     * 参考{@link TimeAlignmentEnum}
     */
    private String timeSegment;

    /**
     * 业务作用开始时间.
     */
    private Long beginTime;

    /**
     * 业务作用结束时间.
     */
    private Long endTime;

    /**
     * 获取唯一键.
     *
     * @return 唯一键
     */
    public Pair<String, Long> getGlobalEventKey() {
        return Pair.of(id, eventTime);
    }
}

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

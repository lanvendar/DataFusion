package com.datafusion.scheduler.master.trigger.model;

import com.datafusion.scheduler.enums.StatusEnum;
import lombok.Data;

import java.util.Objects;

/**
 * 调度实例对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/3/9
 * @since 2023/3/9
 */
@Data
public class TriggerInstance implements Comparable<TriggerInstance> {

    /**
     * 调度对象id.
     */
    private String payloadId;

    /**
     * 被调度对象的版本.
     *
     * <p>
     * <br>1.由框架本身内部产生的版本.
     * <br>2.用于隔离前后两次发布后是同一个payloadId,隔离取上一次的实例.
     * <br>3.目前对应外部字段为 publish_time,后期考虑是否整合 flow_version 字段.
     */
    private String version;

    /**
     * 调度对象实例id.
     */
    private String instanceId;

    /**
     * 调度时间.
     */
    private long scheduleTime;

    /**
     * 调度标志.-1:不调度,0:立即调度,>0:延迟调度.
     */
    private long scheduleSign;

    /**
     * 状态.
     */
    private StatusEnum state;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TriggerInstance that = (TriggerInstance) o;
        return payloadId.equals(that.payloadId) && instanceId.equals(that.instanceId) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payloadId, instanceId, version);
    }

    @Override
    public int compareTo(TriggerInstance o) {
        return (int) (this.scheduleTime - o.getScheduleTime());
    }
}

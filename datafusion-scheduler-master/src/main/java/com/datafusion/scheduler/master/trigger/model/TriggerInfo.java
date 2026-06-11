package com.datafusion.scheduler.master.trigger.model;

import com.datafusion.common.cron.CronUtil;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 调度器信息.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/10
 * @since 2022/6/10
 */
@Slf4j
@Data
public class TriggerInfo {
    /**
     * payload id.
     */
    private String payloadId;

    /**
     * payload 被调度对象的版本.
     *
     * <p>
     * <br>1.由框架本身内部产生的版本.
     * <br>2.用于隔离前后两次发布后是同一个payloadId,隔离取上一次的实例.
     * <br>3.目前对应外部字段为 publish_time ,后期考虑是否整合 flow_version 字段.
     */
    private String version;

    /**
     * 调度标志,是否调度.
     */
    private volatile boolean scheduleFlag = true;

    /**
     * 触发器唯一id,triggerId id.
     */
    private String triggerId;

    /**
     * 触发器类型 cron & simple.
     */
    private TriggerTypeEnum triggerType;

    /**
     * 触发器类型表达式.
     */
    private String triggerExpression;

    /**
     * 触发器策略.
     */
    private TriggerPolicyEnum triggerPolicy = TriggerPolicyEnum.EXECUTE_ONCE;

    //private long baseTime = System.currentTimeMillis();
    //private boolean included = true;

    /**
     * 开始时间.
     */
    private long startTime;

    /**
     * 结束时间.
     */
    private long endTime = Long.MAX_VALUE;

    /**
     * 判断是否还有下一次调度.
     *
     * @param baseTime 基准时间
     * @param included 是否包含当前基准时间
     * @return 结果
     */
    public boolean hasNextSchedule(long baseTime, boolean included) {
        long scheduleTime = calScheduleTime(baseTime, included);
        if (scheduleTime == -1) {
            return false;
        } else {
            return scheduleTime <= endTime;
        }
    }

    /**
     * 根据基准时间计算下一次调度时间.
     *
     * @param baseTime 基准时间
     * @param included 是否包含基准时间
     * @return 下一次调度时间
     */
    public long calScheduleTime(long baseTime, boolean included) {
        long scheduleTime = -1;

        //如果基准时间小于开始时间,则取开始时间
        if (baseTime < this.getStartTime()) {
            baseTime = this.getStartTime();
        }
        //下一次调度时间
        if (this.getTriggerType() == TriggerTypeEnum.CRON) {
            if (included) {
                scheduleTime = CronUtil.nextIncluded(this.getTriggerExpression(), new Date(baseTime)).getTime();
            } else {
                scheduleTime = CronUtil.next(this.getTriggerExpression(), new Date(baseTime)).getTime();
            }
        } else if (this.getTriggerType() == TriggerTypeEnum.INTERVAL) {
            long interval = Long.parseLong(this.getTriggerExpression());
            if (included && (baseTime - this.getStartTime()) % interval == 0) {
                scheduleTime = baseTime;
            } else {
                long pass = ((baseTime - this.getStartTime()) / interval + 1) * interval;
                scheduleTime = (this.getStartTime() + pass);
            }
        } else {
            log.warn("不是正确的调度类型枚举");
        }
        //判断调度时间是否大于结束时间
        if (scheduleTime > this.getEndTime()) {
            return -1;
        } else {
            return scheduleTime;
        }
    }

    @Override
    public String toString() {
        return "TriggerInfo{" + "payloadId='" + payloadId + '\'' + ", version=" + version + ", triggerId='"
                + triggerId + '\'' + ", triggerType=" + triggerType + ", triggerExpression='" + triggerExpression
                + '\'' + ", triggerPolicy=" + triggerPolicy + ", startTime=" + startTime + ", endTime=" + endTime
                + ", scheduleFlag=" + scheduleFlag + '}';
    }
}

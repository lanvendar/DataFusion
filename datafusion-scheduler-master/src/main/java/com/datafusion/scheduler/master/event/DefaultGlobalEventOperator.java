package com.datafusion.scheduler.master.event;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.date.DateTimeStamp;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 调度时间范围匹配事件池(默认).
 *
 * @author lanvendar
 * @version 3.0.0, 2024/9/10
 * @since 2022/6/7
 */
@Slf4j
public class DefaultGlobalEventOperator extends AbstractGlobalEventOperator<RangeSet<Long>> {

    /**
     * 初始化构造方法.
     *
     * @param eventStorage    存储
     * @param eventThreadPool 事件通知线程池
     * @param options         调度统一配置
     */
    public DefaultGlobalEventOperator(EventStorage eventStorage, ThreadPoolExecutor eventThreadPool, Options options) {
        super(eventStorage, eventThreadPool, options);
    }

    /**
     * 初始化事件缓存.
     *
     * @param eventStorage 事件存储接口
     * @param options      调度统一配置
     * @return 事件缓存
     */
    @Override
    protected LoadingCache<String, RangeSet<Long>> initEventModel(EventStorage eventStorage, Options options) {
        int eventCacheMaxSize = options.get(MasterConfigOptions.EVENT_INSTANCE_CACHE_MAX_SIZE);
        long eventRetainTime = options.get(MasterConfigOptions.EVENT_RETAIN_TIME);
        int eventRetainNum = options.get(MasterConfigOptions.EVENT_RETAIN_NUM);
        return Caffeine.newBuilder().maximumSize(eventCacheMaxSize)//
                .build(eventId -> {
                    List<GlobalEvent> events =
                            eventStorage.loadByEventId(eventId, eventRetainTime, eventRetainNum);
                    RangeSet<Long> rangeSet = TreeRangeSet.create();
                    if (CollectionUtil.isNotEmpty(events)) {
                        for (GlobalEvent event : events) {
                            Long startTime = getStartTime(event.getBeginTime(), event.getTimeSegment());
                            Long endTime = getEndTime(event.getEndTime(), event.getTimeSegment());
                            event.setBeginTime(startTime);
                            event.setEndTime(endTime);
                            rangeSet.add(Range.openClosed(startTime, endTime));
                        }
                    } else {
                        log.warn("全局业务事件不存在,eventId=[{}]", eventId);
                    }
                    return rangeSet;
                });
    }

    @Override
    boolean checkHandle(RangeSet<Long> indexEventModel, Long eventTime) {
        if (null == indexEventModel) {
            return false;
        }
        return indexEventModel.contains(eventTime);
    }

    @Override
    void saveEvent(GlobalEvent event) {
        //计算事件时间范围
        event.setBeginTime(getStartTime(event.getEventTime(), event.getTimeSegment()));
        event.setEndTime(getEndTime(event.getEventTime(), event.getTimeSegment()));

        String eventKey = getEventIndex(event.getGlobalEventKey());
        super.indexEventModel.asMap().compute(eventKey, (k, v) -> {
            if (v == null) {
                RangeSet<Long> rangeSet = TreeRangeSet.create();
                rangeSet.add(Range.openClosed(event.getBeginTime(), event.getEndTime()));
                v = rangeSet;
            } else {
                v.add(Range.openClosed(event.getBeginTime(), event.getEndTime()));
                log.debug("全局业务事件已经存在,v={}", v);
            }
            super.eventStorage.save(event);
            return v;
        });
    }

    /**
     * 获取周期分片开始时间.
     *
     * @param eventTime   业务时间
     * @param timeSegment 时间分片枚举
     * @return 开始时间
     */
    private Long getStartTime(Long eventTime, String timeSegment) {
        return DateTimeStamp.getTime(eventTime, timeSegment);
    }

    /**
     * 获取周期分片结束时间.
     *
     * @param eventTime   业务时间
     * @param timeSegment 时间分片枚举
     * @return 结束时间
     */
    private Long getEndTime(Long eventTime, String timeSegment) {
        return DateTimeStamp.getTime(eventTime, DateTimeStamp.getNextTimeNature(timeSegment));
    }

}

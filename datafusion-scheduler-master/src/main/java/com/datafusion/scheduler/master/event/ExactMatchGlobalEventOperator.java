package com.datafusion.scheduler.master.event;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.options.Options;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 调度时间精准匹配事件池.
 *
 * @author lanvendar
 * @version 3.0 2022/5/9
 * @since 2022/5/9
 */
@Slf4j
public class ExactMatchGlobalEventOperator extends AbstractGlobalEventOperator<Long> {

    /**
     * 构造器.
     *
     * @param eventStorage event仓库
     * @param eventThreadPool 事件处理线程池
     * @param options         统一调度配置
     */
    public ExactMatchGlobalEventOperator(EventStorage eventStorage, ThreadPoolExecutor eventThreadPool, Options options) {
        super(eventStorage, eventThreadPool, options);
    }

    /**
     * 初始化缓存.
     *
     * @param eventStorage event存储接口
     * @param options      统一调度配置
     * @return 缓存
     */
    protected LoadingCache<String, Long> initEventModel(EventStorage eventStorage, Options options) {
        int eventCacheMaxSize = options.get(MasterConfigOptions.EVENT_INSTANCE_CACHE_MAX_SIZE);
        return Caffeine.newBuilder()//
                .maximumSize(eventCacheMaxSize)//
                .build(key -> {
                    Pair<String, Long> eventKey = revertEventKey(key);
                    GlobalEvent globalEvent = eventStorage.loadByEventKey(eventKey.getKey(), eventKey.getValue());
                    return globalEvent.getEventTime();
                });
    }

    /**
     * 精准匹配模式下,eventIndex 只使用 key 部分.
     *
     * @param eventKey 事件的主键
     * @return 事件索引键
     */
    @Override
    protected String getEventIndex(Pair<String, Long> eventKey) {
        return eventKey.getKey();
    }

    @Override
    public synchronized boolean checkEvents(Pair<String, Long> eventKey, Long eventTime) {
        return indexEventModel.get(getEventIndex(eventKey)) != null;
    }

    @Override
    boolean checkHandle(Long indexEventModel, Long eventTime) {
        return indexEventModel != null && indexEventModel.equals(eventTime);
    }

    @Override
    protected void saveEvent(GlobalEvent event) {
        log.debug("发生消息,event={}", JacksonUtils.tryObj2Str(event));
        String eventKey = getEventIndex(event.getGlobalEventKey());
        indexEventModel.asMap().compute(eventKey, (k, v) -> {
            if (v == null) {
                v = event.getEventTime();
                eventStorage.save(event);
            } else {
                log.warn("全局业务事件已经存在,v={}", v);
            }
            return v;
        });
    }
}

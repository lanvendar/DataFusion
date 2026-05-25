package com.datafusion.scheduler.master.event.storage;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.List;

/**
 * EventStorage 接口的缓存装饰器.
 *
 * @author david
 * @version 3.6.4, 2024/10/21
 * @since 3.6.4, 2024/10/21
 */
public class CachedEventStorage implements EventStorage {

    /**
     * 事件的缓存.
     */
    private final LoadingCache<Pair<String, Long>, GlobalEvent> eventCache;

    /**
     * 事件存储.
     */
    private final EventStorage eventStorage;

    /**
     * 默认构造函数.
     */
    public CachedEventStorage() {
        this(new EventStorageMem(), new Options());
    }

    /**
     * 构造函数.
     *
     * @param eventStorage 事件存储
     * @param options      调度统一配置
     */
    public CachedEventStorage(EventStorage eventStorage, Options options) {
        this.eventStorage = eventStorage;
        int eventCacheMaxSize = options.get(MasterConfigOptions.EVENT_INSTANCE_CACHE_MAX_SIZE);
        this.eventCache = Caffeine.newBuilder().maximumSize(eventCacheMaxSize)
                .build(key -> eventStorage.loadByEventKey(key.getKey(), key.getValue()));
    }

    @Override
    public void save(GlobalEvent globalEvent) {
        eventStorage.save(globalEvent);
    }

    @Override
    public List<GlobalEvent> loadByEventId(String eventId, long retainTime, int retainNum) {
        return eventStorage.loadByEventId(eventId, retainTime, retainNum);
    }

    @Override
    public GlobalEvent loadByEventKey(String eventId, Long eventTime) {
        return eventCache.get(Pair.of(eventId, eventTime));
    }
}

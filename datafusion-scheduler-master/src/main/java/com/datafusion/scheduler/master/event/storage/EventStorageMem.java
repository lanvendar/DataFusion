package com.datafusion.scheduler.master.event.storage;

import com.datafusion.scheduler.master.event.model.GlobalEvent;
import lombok.Getter;

import java.util.List;

/**
 * 全局事件内存存储实现(默认).
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/4
 * @since 2024/11/4
 */
@Getter
public class EventStorageMem implements EventStorage {
    @Override
    public void save(GlobalEvent globalEvent) {

    }

    @Override
    public List<GlobalEvent> loadByEventId(String eventId, long retainTime, int retainNum) {
        return null;
    }

    @Override
    public GlobalEvent loadByEventKey(String eventId, Long eventTime) {
        return null;
    }
}

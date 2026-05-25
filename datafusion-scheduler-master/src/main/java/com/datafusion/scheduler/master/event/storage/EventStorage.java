package com.datafusion.scheduler.master.event.storage;

import com.datafusion.scheduler.master.event.model.GlobalEvent;

import java.util.List;

/**
 * 全局事件存储接口.
 *
 * @author david
 * @version 3.6.4, 2024/10/21
 * @since 3.6.4, 2024/10/21
 */
public interface EventStorage {

    /**
     * 保存事件实例.
     *
     * @param globalEvent event 实例
     */
    void save(GlobalEvent globalEvent);

    /**
     * 获取某个事件的所有实例.
     *
     * @param eventId    事件实例 id
     * @param retainTime 保留时间
     * @param retainNum  保留数量
     * @return 事件实例集合
     */
    List<GlobalEvent> loadByEventId(String eventId, long retainTime, int retainNum);

    /**
     * 获取某个事件的实例.
     *
     * @param eventId   事件实例 id
     * @param eventTime 事件发生时间
     * @return 事件实例
     */
    GlobalEvent loadByEventKey(String eventId, Long eventTime);
}

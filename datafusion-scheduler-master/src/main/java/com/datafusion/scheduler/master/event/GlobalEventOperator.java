package com.datafusion.scheduler.master.event;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.master.event.model.GlobalEvent;

/**
 * 事件操作类.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/6
 * @since 2022/6/6
 */
public interface GlobalEventOperator {

    /**
     * 检查某个流程或者任务实例的依赖是否成功.
     *
     * @param eventKey  依赖事件主键
     * @param eventTime 本事件业务时间
     * @return 是否成功
     */
    boolean checkEvents(Pair<String, Long> eventKey, Long eventTime);

    /**
     * 注册某个流程或者任务实例事件,初始化任务的时候使用.
     *
     * @param eventKey 本事件主键
     * @param listener 监听器
     */
    void registerListener(Pair<String, Long> eventKey, GlobalEventListener listener);

    /**
     * 产生某个流程或者任务实例事件,任务成功或人工干预时时使用.
     *
     * @param event 流程或者任务实例对应的事件实体
     */
    void occurredEvent(GlobalEvent event);
}

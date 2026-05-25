package com.datafusion.scheduler.master.event;

import com.datafusion.scheduler.master.event.model.GlobalEvent;

/**
 * 全局事件监听器.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/6
 * @since 2022/6/6
 */
public interface GlobalEventListener {
    /**
     * 通知某个流程或者任务实例的依赖是否成功.
     *
     * @param event 流程或者任务实例
     */
    void notify(GlobalEvent event);
}

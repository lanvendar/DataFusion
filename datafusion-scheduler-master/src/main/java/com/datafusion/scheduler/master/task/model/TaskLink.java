package com.datafusion.scheduler.master.task.model;

import com.datafusion.common.graph.Link;

/**
 * task上下游关系.
 *
 * @author 李正凯
 * @version 3.0 2022/4/22
 * @since 2022/4/22
 */
public class TaskLink extends Link<String> {

    /**
     * 参数节点link.
     *
     * @param id      边 id.
     * @param startId 开始节点 id.
     * @param endId   终止节点 id.
     */
    public TaskLink(String id, String startId, String endId) {
        super(id, startId, endId);
    }

    /**
     * 参数节点link.
     */
    public TaskLink() {
        super();
    }
}
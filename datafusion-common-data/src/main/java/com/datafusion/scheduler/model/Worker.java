package com.datafusion.scheduler.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 调度的工作节点.
 *
 * @author 李正凯
 * @version 3.0, 2022/5/10
 * @since 2022/5/10
 */
@Data
public class Worker {

    /**
     * 上线状态.
     */
    public static final Integer STATUS_UP = 1;

    /**
     * 下线状态.
     */
    public static final Integer STATUS_DOWN = 0;

    /**
     * 清除状态.
     */
    public static final Integer STATUS_DELETED = 2;

    /**
     * 工作节点id.
     */
    private String id;

    /**
     * 工作节点ip.
     */
    private String ip;

    /**
     * 工作节点端口.
     */
    private Integer port;

    /**
     * 工作节点支持的插件.
     */
    private List<String> pluginTypes = new ArrayList<>();

    /**
     * 工作节点状态.
     */
    private Integer status;

    /**
     * 工作节点主机名.
     */
    private String hostName;

    /**
     * 首次注册时间.
     */
    private Long registerTime;

    /**
     * 最近心跳时间.
     */
    private Long lastHeartbeatTime;

    /**
     * 最近更新时间.
     */
    private Long updateTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Worker worker = (Worker) o;
        return Objects.equals(id, worker.id) && Objects.equals(hostName, worker.hostName)
                && Objects.equals(port, worker.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hostName, port);
    }

    /**
     * 节点是否存活.
     *
     * @return true or false
     */
    public boolean isAlive() {
        return status != null && status.equals(STATUS_UP);
    }
}

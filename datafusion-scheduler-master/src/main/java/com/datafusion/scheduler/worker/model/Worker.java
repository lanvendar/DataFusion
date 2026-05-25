/*
 * Copyright © 2020-2022 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

package com.datafusion.scheduler.worker.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 调度的工作节点.
 *
 * @author 李正凯
 * @version 3.0 2022/5/10
 * @since 2022/5/10
 */
@Data
public class Worker {

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Worker worker = (Worker) o;
        return id.equals(worker.id) && hostName.equals(worker.hostName) && port.equals(worker.port);
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

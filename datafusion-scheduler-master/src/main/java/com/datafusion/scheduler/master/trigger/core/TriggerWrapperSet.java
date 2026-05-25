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

package com.datafusion.scheduler.master.trigger.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 触发器类,实现延迟队列.
 *
 * @param <T> 触发的payload
 * @author 李正凯
 * @version 3.0 2022/5/18
 * @since 2022/4/24
 */
@Slf4j
@Data
public class TriggerWrapperSet<T> implements Delayed {

    /**
     * 触发时间.
     */
    private long triggerTimeMs;

    /**
     * 调度对象集合.
     */

    private Set<TriggerWrapper<T>> targetSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(triggerTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        long d = this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        return (d == 0) ? 0 : ((d > 0) ? 1 : -1);
    }

    /**
     * 添加调度对象.
     *
     * @param t 调度对象
     */
    void addTarget(TriggerWrapper<T> t) {
        if (!targetSet.add(t)) {
            log.warn("调度对象[{}]已存在set中", t);
        }
    }
}

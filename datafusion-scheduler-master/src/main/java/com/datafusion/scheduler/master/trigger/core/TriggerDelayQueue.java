package com.datafusion.scheduler.master.trigger.core;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 触发队列.
 *
 * @param <T> 触发器的实体对象
 * @author 李正凯
 * @version 3.0 2022/5/18
 * @since 2022/5/10
 */
@Slf4j
public class TriggerDelayQueue<T> {

    /**
     * 延迟队列.
     */
    private final DelayQueue<TriggerWrapperSet<T>> delayQueue = new DelayQueue<>();

    /**
     * 触发时间与触发对象的映射map.
     */
    private final ConcurrentHashMap<Long, TriggerWrapperSet<T>> triggerMap = new ConcurrentHashMap<>();

    /**
     * 入队.
     *
     * @param triggerWrapper 调度对象
     */
    public void enqueue(TriggerWrapper<T> triggerWrapper) {
        triggerMap.compute(triggerWrapper.getTriggerTime(), (k, v) -> {
            if (v == null) {
                v = new TriggerWrapperSet<>();
                v.setTriggerTimeMs(k);
                v.addTarget(triggerWrapper);

                //加入delay queue之后才能够被消费者获取
                if (!delayQueue.add(v)) {
                    log.warn("trigger info=[{}]无法加入delay queue", v);
                }
            } else {
                //此时有可能triggerInfo已被消费者获取，但是Dequeue中删除triggerMap对应key时一定会等待（因为现在处于triggerMap的compute中）
                v.addTarget(triggerWrapper);
            }

            return v;
        });
    }

    /**
     * 入队.
     *
     * @param t           payload
     * @param triggerTime 调度时间
     */
    public void enqueue(T t, long triggerTime) {
        TriggerWrapper<T> triggerWrapper = new TriggerWrapper<>();
        triggerWrapper.setTriggerTime(triggerTime);
        triggerWrapper.setPayload(t);
        enqueue(triggerWrapper);
    }

    /**
     * 阻塞出队.
     *
     * @return 调度对象
     */
    public List<T> dequeue() throws InterruptedException {
        TriggerWrapperSet<T> triggerWrapperSet = delayQueue.take();
        synchronized (this) {
            triggerMap.remove(triggerWrapperSet.getTriggerTimeMs());
            return triggerWrapperSet.getTargetSet().stream().filter(s -> !s.isCancelled()).map(TriggerWrapper::getPayload)
                    .collect(Collectors.toList());
        }

    }

    /**
     * 有超时的出队.
     *
     * @param timeout timeout
     * @param unit    unit
     * @return 如果超时返回null否则返回调度对象
     */
    public List<T> dequeue(long timeout, TimeUnit unit) {
        try {
            TriggerWrapperSet<T> triggerWrapperSet = delayQueue.poll(timeout, unit);
            if (null != triggerWrapperSet) {
                synchronized (this) {
                    triggerMap.remove(triggerWrapperSet.getTriggerTimeMs());
                    return triggerWrapperSet.getTargetSet().stream().filter(s -> !s.isCancelled())
                            .map(TriggerWrapper::getPayload).collect(Collectors.toList());
                }
            } else {
                return null;
            }

        } catch (InterruptedException e) {
            log.error("线程被中断");
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 删除对应的ScheduleTarget.
     *
     * @param t payload对象
     * @return 调度对象列表
     */
    public List<TriggerWrapper<T>> remove(T t) {
        //fixme: 寻找payload效率比较低
        List<TriggerWrapper<T>> list = null;
        synchronized (this) {
            list = triggerMap.values().stream().flatMap(x -> x.getTargetSet().stream())
                    .filter(w -> w.getPayload().equals(t)).collect(Collectors.toList());
            list.forEach(w -> w.setCancelled(true));
        }
        if (CollectionUtil.isEmpty(list)) {
            return null;
        } else {
            return new ArrayList<>(list);
        }
    }

    /**
     * 导出所有可输出的Entry.
     *
     * @return 所有可输出的Entry列表
     */
    public List<T> tryDrain() {
        List<TriggerWrapperSet<T>> list = new ArrayList<>();
        delayQueue.drainTo(list);
        if (CollectionUtil.isNotEmpty(list)) {

            List<T> resultList = new ArrayList<>();

            for (TriggerWrapperSet<T> triggerWrapperSet : list) {
                synchronized (this) {
                    triggerMap.remove(triggerWrapperSet.getTriggerTimeMs());
                    resultList.addAll(triggerWrapperSet.getTargetSet().stream().filter(s -> !s.isCancelled())
                            .map(TriggerWrapper::getPayload).collect(Collectors.toList()));
                }
            }

            return resultList;
        } else {
            return null;
        }
    }
}

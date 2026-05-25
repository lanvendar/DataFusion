package com.datafusion.scheduler.master.event;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局事件处理抽象类.
 *
 * @param <M> 事件对象检索模型
 * @author lanvendar
 * @version 1.0.0, 2024/11/7
 * @since 2024/11/7
 */
@Slf4j
public abstract class AbstractGlobalEventOperator<M> implements GlobalEventOperator {
    /**
     * 事件对应的监听器的Map.
     */
    private final ConcurrentHashMap<String, Set<GlobalEventListener>> listenerMap = new ConcurrentHashMap<>();

    /**
     * 存储.
     */
    protected final EventStorage eventStorage;

    /**
     * 事件索引模型.
     */
    protected final LoadingCache<String, M> indexEventModel;

    /**
     * notify的线程池.
     */
    private final ExecutorService eventThreadPool;

    /**
     * 初始化构造方法.
     *
     * @param eventStorage    存储
     * @param eventThreadPool 事件通知线程池
     * @param options         调度统一配置
     */
    protected AbstractGlobalEventOperator(EventStorage eventStorage, ThreadPoolExecutor eventThreadPool, Options options) {
        this.eventStorage = eventStorage;
        this.indexEventModel = initEventModel(eventStorage, options);
        this.eventThreadPool = eventThreadPool;
    }

    /**
     * 初始化索引缓存.
     *
     * @param eventStorage 存储
     * @param options      调度统一配置
     * @return 缓存
     */
    abstract LoadingCache<String, M> initEventModel(EventStorage eventStorage, Options options);

    /**
     * 加密事件索引模型建.
     *
     * @param eventKey 事件的主键
     * @return 事件处理器
     */
    protected String getEventIndex(Pair<String, Long> eventKey) {
        return eventKey.getKey() + SystemConstant.UNDER_LINE + eventKey.getValue();
    }

    /**
     * 解密事件索引模型建.
     *
     * @param eventKey 事件的主键
     * @return 事件处理器
     */
    protected Pair<String, Long> revertEventKey(String eventKey) {
        String[] split = eventKey.split(SystemConstant.UNDER_LINE);
        return Pair.of(split[0], Long.valueOf(split[1]));
    }

    @Override
    public synchronized boolean checkEvents(Pair<String, Long> eventKey, Long eventTime) {
        M model = indexEventModel.get(getEventIndex(eventKey));
        return checkHandle(model, eventTime);
    }

    /**
     * 检查是否可以处理.
     *
     * @param indexEventModel 索引模型
     * @param eventTime       事件时间
     * @return 是否可以处理
     */
    abstract boolean checkHandle(M indexEventModel, Long eventTime);

    @Override
    public void registerListener(Pair<String, Long> eventKey, GlobalEventListener listener) {
        String key = getEventIndex(eventKey);
        listenerMap.compute(key, (k, v) -> {
            if (v == null) {
                v = Collections.newSetFromMap(new ConcurrentHashMap<GlobalEventListener, Boolean>());
            }
            v.add(listener);
            return v;
        });
    }

    @Override
    public synchronized void occurredEvent(GlobalEvent event) {
        //保存事件
        saveEvent(event);
        //通知监听器
        String eventKey = getEventIndex(event.getGlobalEventKey());
        Set<GlobalEventListener> listenerSet = listenerMap.remove(eventKey);
        if (CollectionUtil.isNotEmpty(listenerSet)) {
            for (GlobalEventListener listener : listenerSet) {
                ListenableFuture<Void> futureTask = Futures.submit(() -> listener.notify(event), eventThreadPool);
                Futures.addCallback(futureTask, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void o) {
                        //TODO 成功后操作
                        log.debug("eventKey={}的 listener 通知成功", eventKey);
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        //TODO 失败重试listener通知
                        log.warn("eventKey={}的 listener 通知失败", eventKey + e);
                    }
                }, MoreExecutors.directExecutor());
            }
        }
    }

    /**
     * 保存事件.
     *
     * @param event 事件
     */
    abstract void saveEvent(GlobalEvent event);
}

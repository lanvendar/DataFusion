package com.datafusion.scheduler.master.trigger.core;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 调度线程触发类.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/17
 * @since 2024/12/17
 */
@Slf4j
public abstract class TriggerThread extends Thread {

    /**
     * 停止标志.
     */
    private volatile boolean stopped = false;

    /**
     * 执行线程.
     */
    private final ThreadPoolExecutor executors;

    /**
     * 监听线程.
     */
    private final Executor listeningExecutor;

    /**
     * 触发线程名称.
     */
    private final String threadName;

    /**
     * 构造函数.
     *
     * @param threadName 线程名
     * @param executors  线程池
     */
    protected TriggerThread(String threadName, ThreadPoolExecutor executors) {
        this.threadName = threadName;
        this.executors = executors;
        this.listeningExecutor = MoreExecutors.directExecutor();
    }

    /**
     * 启动.
     */
    @Override
    public synchronized void start() {
        super.setName(threadName);
        super.start();
    }

    /**
     * 拉取调度实例.
     *
     * @return 调度实例列表
     */
    abstract List<TriggerInstance> fetchInstances();

    /**
     * 触发事件.
     *
     * @param instance 调度实例
     * @return 是否成功
     */
    abstract Boolean triggerAction(TriggerInstance instance);

    /**
     * 触发事件成功回调.
     *
     * @param result   是否成功
     * @param instance 调度实例
     */
    abstract void actionSuccess(Boolean result, TriggerInstance instance);

    /**
     * 触发事件失败回调.
     *
     * @param instance 调度实例
     */
    abstract void actionFailure(TriggerInstance instance);

    /**
     * 关闭.
     */
    public void close() {
        this.stopped = true;
        this.executors.shutdown();
        try {
            boolean terminated = executors.awaitTermination(10, TimeUnit.SECONDS);
            log.debug("等待触发事件回调线程池关闭结果:{}", terminated);
            if (!terminated) {
                executors.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行.
     */
    @Override
    public void run() {
        log.info("{} 开始拉取调度实例", this.getName());
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        while (!stopped) {
            stopwatch.start();
            try {
                // 1. 获取下一批调度实例
                List<TriggerInstance> list = fetchInstances();
                if (CollectionUtil.isNotEmpty(list)) {
                    for (TriggerInstance instance : list) {
                        // 异步初始化或分发
                        ListenableFuture<Boolean> future = Futures.submit(() -> triggerAction(instance), executors);

                        // 异步回调
                        Futures.addCallback(future, new FutureCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                actionSuccess(result, instance);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.error("触发事件回调失败", t);
                                actionFailure(instance);
                            }
                        }, listeningExecutor);
                    }
                }
            } finally {
                if (stopwatch.elapsed(TimeUnit.MILLISECONDS) < 1000) {
                    // 秒对齐.
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                    } catch (InterruptedException e) {
                        if (!stopped) {
                            log.error(e.getMessage(), e);
                        }
                        Thread.currentThread().interrupt();
                    }
                }
                stopwatch.reset();
            }
        }
    }
}

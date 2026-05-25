package com.datafusion.common.threadpool;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池创建器{@link ThreadPoolExecutor}.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/05/17
 * @since 2021-09-29
 */
@Slf4j
public class ThreadPoolBuilder implements Builder<ThreadPoolExecutor> {

    /**
     * 线程池核心线程数,默认值:可用处理器的Java虚拟机的数量,{@code Runtime.getRuntime().availableProcessors()}.
     */
    private int corePoolSize = Runtime.getRuntime().availableProcessors();

    /**
     * 线程池最大线程数,默认值:可用处理器的Java虚拟机的数量*2,{@code Runtime.getRuntime().availableProcessors()*2}.
     */
    private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 线程池最大空闲时间,默认值 60 秒.
     */
    private int keepAliveSeconds = 60;

    /**
     * 空闲时间单位,默认值 秒.
     */
    private TimeUnit unit = TimeUnit.SECONDS;

    /**
     * 被提交的任务队大小,{@code Integer.MAX_VALUE}.
     */
    private int queueCapacity = Integer.MAX_VALUE;

    /**
     * 线程池满的情况下默认拒绝调用线程.
     */
    private boolean rejectFlag = false;

    /**
     * 空闲时是否回收核心线程,默认值 {@code false}.
     */
    private boolean allowCoreThreadTimeOut = false;

    /**
     * 线程工厂，用于自定义线程创建.
     */
    private ThreadFactory threadFactory;

    /**
     * 线程池名称.
     */
    private String poolName;

    /**
     * 线程池对象.
     */
    private ThreadPoolMonitor threadPoolExecutor;

    /**
     * 设置 ThreadPoolExecutor 的核心池大小,默认为 1.
     *
     * @param corePoolSize 线程池核心线程数
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;

    }

    /**
     * 设置线程池最大线程数,默认值 {@code Integer.MAX_VALUE}.
     *
     * <p>可以在运行时修改此设置，例如通过 JMX.
     *
     * @param maxPoolSize 线程池最大线程数
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * 设置线程池最大空闲时间. 默认值 60 秒.
     *
     * @param keepAliveSeconds 线程池最大空闲时间
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
        return this;
    }

    /**
     * 设置线程池名称.
     *
     * @param poolName 线程池名称
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setPoolName(String poolName) {
        this.poolName = poolName;
        return this;
    }

    /**
     * 设置空闲时是否回收核心线程.默认值 false,不回收.
     *
     * @param allowCoreThreadTimeOut 是否回收核心线程,默认值{@code false}
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
        return this;
    }

    /**
     * 线程池满的情况下默认拒绝调用线程,默认值 false.
     *
     * <p>拒绝策略true:回调,原线程处理;false:拒绝处理.
     *
     * @param rejectFlag 调用线程拒绝策略,默认值{@code false}
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setRejectFlag(boolean rejectFlag) {
        this.rejectFlag = rejectFlag;
        return this;
    }

    /**
     * 设置 拒绝策略 RejectedExecutionHandler 用于 ExecutorService.
     *
     * <p>默认{@code rejectFlag = false}超出部分是丢弃此线程.
     *
     * @see ThreadPoolExecutor.AbortPolicy
     */
    private static RejectedExecutionHandler setRejectedExecutionHandler(Boolean rejectFlag) {
        //线程池满的情况下默认拒绝调用线程
        if (rejectFlag == true) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        } else {
            return new ThreadPoolExecutor.AbortPolicy();
        }
    }

    /**
     * 设置线程池阻塞的队列的容量.默认值{@code Integer.MAX_VALUE}.
     *
     * <p>任何正整数都会是{@code LinkedBlockingQueue}类型的队列实例,其他值则为{@code SynchronousQueue}类型的队列实例.</p>
     *
     * @param queueCapacity 队列的容量
     * @return {@link ThreadPoolBuilder}本对象
     * @see LinkedBlockingQueue
     * @see SynchronousQueue
     */
    public ThreadPoolBuilder setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    /**
     * 设置线程工厂，用于自定义线程创建.
     *
     * @param threadFactory 线程工厂
     * @return {@link ThreadPoolBuilder}本对象
     */
    public ThreadPoolBuilder setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * 创建线程池的队列.
     *
     * <p>任何正整数都会是{@code LinkedBlockingQueue}类型的队列实例,其他值则为{@code SynchronousQueue}类型的队列实例.</p>
     *
     * @param queueCapacity 队列的容量
     * @return 返回{@code LinkedBlockingQueue}队列或者{@code SynchronousQueue}队列
     * @see LinkedBlockingQueue
     * @see SynchronousQueue
     */
    private static BlockingQueue<Runnable> createQueue(int queueCapacity) {
        if (queueCapacity > 0) {
            return new LinkedBlockingQueue<Runnable>(queueCapacity);
        } else {
            return new SynchronousQueue<Runnable>();
        }
    }

    /**
     * 创建ThreadPoolBuilder,开始构建.
     *
     * @return this
     */
    public static ThreadPoolBuilder create() {
        return new ThreadPoolBuilder();
    }

    @Override
    public ThreadPoolExecutor build() {
        return build(this);
    }

    /**
     * 构建ThreadPoolExecutor.
     *
     * @param builder this
     * @return {@link ThreadPoolExecutor}
     */
    private static ThreadPoolExecutor build(ThreadPoolBuilder builder) {
        ThreadFactory threadFactory = null;

        //线程池工厂
        String poolName = null;
        if (StrUtil.isNotBlank(builder.poolName)) {
            threadFactory = new NamedThreadFactory(builder.poolName);
            poolName = builder.poolName;
        } else {
            threadFactory = (null != builder.threadFactory) ? builder.threadFactory : Executors.defaultThreadFactory();
        }
        //线程池容量
        BlockingQueue<Runnable> queue = createQueue(builder.queueCapacity);
        //线程池满的情况下默认拒绝调用线程
        RejectedExecutionHandler rejectedExecutionHandler = setRejectedExecutionHandler(builder.rejectFlag);

        ThreadPoolExecutor executor = new ThreadPoolMonitor(
                //核心线程数
                builder.corePoolSize,
                //最大线程数
                builder.maxPoolSize,
                //线程空闲时间
                builder.keepAliveSeconds,
                //线程空闲时间单位
                builder.unit,
                //队列长度0:SynchronousQueue;非0:LinkedBlockingQueue
                queue,
                //线程工厂
                threadFactory,
                //拒绝策略true:回调,原线程处理;false:拒绝处理
                rejectedExecutionHandler,
                //线程池名称
                poolName);
        //是否启用核心线程超时时间
        if (builder.allowCoreThreadTimeOut) {
            executor.allowCoreThreadTimeOut(true);
        }
        //核心线程池预启动
        executor.prestartCoreThread();
        //创建线程池
        log.info(String.format(
                "[创建启动线程池][" + builder.poolName + "-monitor]:PoolSize:%d,CorePoolSize:%d," + "CompletedTask:%d,RunningTask:%d,QueueTask:%d,"
                        + "LargestPoolSize:%d,MaximumPoolSize:%d,KeepAliveTime:%d s", executor.getPoolSize(), executor.getCorePoolSize(),
                executor.getCompletedTaskCount(), executor.getActiveCount(), executor.getQueue().size(), executor.getLargestPoolSize(),
                executor.getMaximumPoolSize(), executor.getKeepAliveTime(TimeUnit.SECONDS)));
        return executor;
    }
}

package com.datafusion.common.threadpool;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ThreadFactory创建器.
 *
 * <p>参考：Guava的ThreadFactoryBuilder
 *
 * @author lanvendar
 * @version 1.0.0, 2022/05/16
 * @since 2021-09-29
 */
public class ThreadFactoryBuilder implements Builder<ThreadFactory> {
    
    /**
     * 用于线程创建的线程工厂类.
     */
    private ThreadFactory threadFactory;
    
    /**
     * 是否守护线程，默认false.
     */
    private Boolean daemon;
    
    /**
     * 线程优先级.
     */
    private Integer priority;
    
    /**
     * 线程池名前缀.
     */
    private String namePrefix;
    
    /**
     * 未捕获异常处理器.
     */
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    
    /**
     * 创建 {@link ThreadFactoryBuilder}.
     *
     * @return {@link ThreadFactoryBuilder}
     */
    public static ThreadFactoryBuilder create() {
        return new ThreadFactoryBuilder();
    }
    
    /**
     * 设置线程池名前缀.
     *
     * @param namePrefix 线程池名前缀
     * @return {@link ThreadFactoryBuilder}本对象
     */
    public ThreadFactoryBuilder setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
        return this;
    }
    
    /**
     * 设置线程创建工厂.
     *
     * @param threadFactory 线程创建工厂
     * @return {@link ThreadFactoryBuilder}本对象
     */
    public ThreadFactoryBuilder setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }
    
    /**
     * 是否守护线程,默认false.
     *
     * @param daemon 是否守护线程
     * @return {@link ThreadFactoryBuilder}本对象
     */
    public ThreadFactoryBuilder setDaemon(Boolean daemon) {
        this.daemon = daemon;
        return this;
    }
    
    /**
     * 线程优先级,默认值@{code Thread.NORM_PRIORITY}
     *
     * @param priority 线程优先级
     * @return {@link ThreadFactoryBuilder}本对象
     */
    public ThreadFactoryBuilder setPriority(Integer priority) {
        if (priority < Thread.MIN_PRIORITY) {
            throw new IllegalArgumentException("Thread priority must be >= " + Thread.MIN_PRIORITY);
        }
        if (priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("Thread priority must be >= " + Thread.MAX_PRIORITY);
        }
        this.priority = priority;
        return this;
    }
    
    /**
     * 未捕获异常处理器.
     *
     * @param handler 未捕获异常处理器.
     * @return {@link ThreadFactoryBuilder}本对象
     */
    public ThreadFactoryBuilder setUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
        this.uncaughtExceptionHandler = handler;
        return this;
    }
    
    /**
     * 创建结构构造器.
     *
     * @return {@link ThreadFactoryBuilder}本对象
     */
    @Override
    public ThreadFactory build() {
        return build(this);
    }
    
    /**
     * 构建.
     *
     * @param builder {@link ThreadFactoryBuilder}
     * @return {@link ThreadFactory}
     */
    private static ThreadFactory build(ThreadFactoryBuilder builder) {
        final ThreadFactory threadFactory =
                (null != builder.threadFactory) ? builder.threadFactory : Executors.defaultThreadFactory();
        final String namePrefix = builder.namePrefix;
        final Boolean daemon = builder.daemon;
        final Integer priority = builder.priority;
        final UncaughtExceptionHandler handler = builder.uncaughtExceptionHandler;
        //线程号,创建个数,默认值为 1开始升序.
        final AtomicLong count = (null == namePrefix) ? null : new AtomicLong(1L);
        return r -> {
            final Thread thread = threadFactory.newThread(r);
            if (null != namePrefix) {
                thread.setName(namePrefix + count.getAndIncrement());
            }
            if (null != daemon) {
                thread.setDaemon(daemon);
            }
            if (null != priority) {
                thread.setPriority(priority);
            }
            if (null != handler) {
                thread.setUncaughtExceptionHandler(handler);
            }
            return thread;
        };
    }
}

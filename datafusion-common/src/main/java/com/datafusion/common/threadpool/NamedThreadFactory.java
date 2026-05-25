package com.datafusion.common.threadpool;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 创建线程工厂类.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/05/17
 * @since 2022/05/17
 */
public class NamedThreadFactory implements ThreadFactory {
    
    /**
     * 线程组号.
     */
    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
    
    /**
     * 线程号.
     */
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    
    /**
     * 命名前缀.
     */
    private final String namePrefix;
    
    /**
     * 线程组.
     */
    private final ThreadGroup group;
    
    /**
     * 是否守护线程.
     */
    private final Boolean daemon;
    
    /**
     * 是否守护线程.
     */
    private final Integer priority;
    
    /**
     * 无法捕获的异常统一处理.
     */
    private final UncaughtExceptionHandler handler;
    
    /**
     * 构造.
     *
     * @param namePrefix 线程名前缀
     */
    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, null, false, null, Thread.NORM_PRIORITY);
    }
    
    /**
     * 构造.
     *
     * @param namePrefix 线程名前缀
     * @param daemon     是否守护线程
     */
    public NamedThreadFactory(String namePrefix, Boolean daemon) {
        this(namePrefix, null, daemon, null, Thread.NORM_PRIORITY);
    }
    
    /**
     * 构造.
     *
     * @param prefix      线程名前缀
     * @param threadGroup 线程组，可以为null
     * @param daemon      是否守护线程
     */
    public NamedThreadFactory(String prefix, ThreadGroup threadGroup, Boolean daemon) {
        this(prefix, threadGroup, daemon, null, Thread.NORM_PRIORITY);
    }
    
    /**
     * 构造.
     *
     * @param prefix      线程名前缀
     * @param threadGroup 线程组，可以为null
     * @param daemon      是否守护线程
     * @param handler     未捕获异常处理
     */
    public NamedThreadFactory(String prefix, ThreadGroup threadGroup, Boolean daemon,
            UncaughtExceptionHandler handler) {
        this(prefix, threadGroup, daemon, handler, Thread.NORM_PRIORITY);
    }
    
    /**
     * 构造.
     *
     * @param namePrefix  线程名前缀
     * @param threadGroup 线程组，可以为null
     * @param daemon      是否守护线程
     * @param handler     未捕获异常处理
     * @param priority    线程优先级
     */
    public NamedThreadFactory(String namePrefix, ThreadGroup threadGroup, Boolean daemon,
            UncaughtExceptionHandler handler, Integer priority) {
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        if (null == namePrefix || "".equals(namePrefix)) {
            this.namePrefix = "custom";
        } else {
            this.namePrefix = namePrefix + "-pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
        }
        this.daemon = daemon;
        this.handler = handler;
        this.priority = priority;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        final Thread t = new Thread(this.group, r, namePrefix + threadNumber.getAndIncrement(), 0);
        //守护线程
        if (null != daemon) {
            t.setDaemon(daemon);
        }
        //优先级
        if (null != priority) {
            t.setPriority(priority);
        }
        //异常处理
        if (null != handler) {
            t.setUncaughtExceptionHandler(handler);
        }
        return t;
    }
    
}

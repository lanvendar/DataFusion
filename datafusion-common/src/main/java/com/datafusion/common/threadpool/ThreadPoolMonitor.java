package com.datafusion.common.threadpool;

import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程类封装ThreadPoolExecutor,增强打印日志监控.
 *
 * <p>支持运行中:corePoolSize,maxPoolSize,keepAliveSeconds,coreTimeOutFlag的修改.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/05/17
 * @since 2021-09-29
 */
@Slf4j
public class ThreadPoolMonitor extends ThreadPoolExecutor {
    
    /**
     * 保存线程开始时间.
     */
    private ConcurrentHashMap<String, Date> startTimes;
    
    /**
     * 线程池名称.
     */
    private String poolName;
    //tomcat中重置并发数量
    //private AtomicInteger atomicInteger;
    
    /**
     * 构造方法.
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   线程空闲时间
     * @param unit            线程空闲时间单位
     * @param workQueue       工作队列
     * @param threadFactory   线程工厂
     * @param handler         拒绝策略
     * @param poolName        线程池名称
     */
    public ThreadPoolMonitor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler,
            String poolName) {
        
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.poolName = poolName;
        this.startTimes = new ConcurrentHashMap<>();
        // TODO Auto-generated constructor stub
    }
    
    @Override
    public List<Runnable> shutdownNow() {
        log.debug(String.format("[" + this.poolName + "-monitor]: going to shutdown now: "
                        + "CompletedTask:%d,RunningTask:%d,QueueTask:%d", this.getCompletedTaskCount(), this.getActiveCount(),
                this.getQueue().size()));
        return super.shutdownNow();
        
    }
    
    @Override
    public void beforeExecute(Thread t, Runnable r) {
        startTimes.put(t.hashCode() + "-" + r.hashCode(), new Date());
    }
    
    @Override
    public void afterExecute(Runnable r, Throwable t) {
        Date startDate = startTimes.remove(Thread.currentThread().hashCode() + "-" + r.hashCode());
        if (startDate == null) {
            startDate = new Date(0L);
        }
        Date finishDate = new Date();
        long cost = (finishDate.getTime() - startDate.getTime());
        
        //tomcat中重置并发数量
        //atomicInteger.decrementAndGet();
        
        log.debug(String.format("[" + this.poolName + "-monitor]: ThreadCostTime:%d ms,PoolSize:%d,CorePoolSize:%d,"
                        + "CompletedTask:%d,RunningTask:%d,QueueTask:%d,"
                        + "LargestPoolSize:%d,MaximumPoolSize:%d,KeepAliveTime:%d s", cost, this.getPoolSize(),
                this.getCorePoolSize(), this.getCompletedTaskCount(), this.getActiveCount(), this.getQueue().size(),
                this.getLargestPoolSize(), this.getMaximumPoolSize(), this.getKeepAliveTime(TimeUnit.SECONDS)));
    }
}

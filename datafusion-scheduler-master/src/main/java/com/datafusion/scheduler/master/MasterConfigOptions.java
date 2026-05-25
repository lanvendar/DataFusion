package com.datafusion.scheduler.master;

import com.datafusion.common.options.ConfigOption;

import static com.datafusion.common.options.ConfigOptions.key;

/**
 * 调度配置类.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/15
 * @since 2022/5/7
 */
public class MasterConfigOptions {
    //region: actor线程池配置

    /**
     * actor 线程池core size.
     */
    public static final ConfigOption<Integer> ACTOR_POOL_CORE_SIZE = key("master.actor.pool.core-size")
            .intType()
            .defaultValue(20)
            .withDescription("actor 线程池core size.");

    /**
     * actor 线程池max size.
     */
    public static final ConfigOption<Integer> ACTOR_POOL_MAX_SIZE = key("master.actor.pool.max-size")
            .intType()
            .defaultValue(40)
            .withDescription("actor 线程池max size.");

    /**
     * actor 线程池keepalive time(秒).
     */
    public static final ConfigOption<Integer> ACTOR_POOL_KEEP_ALIVE_TIME = key("master.actor.pool.keep-alive-time")
            .intType()
            .defaultValue(60)
            .withDescription("actor 线程池keepalive time(秒).");

    /**
     * actor 线程池队列大小.
     */
    public static final ConfigOption<Integer> ACTOR_POOL_CAPACITY = key("master.actor.pool.capacity")
            .intType()
            .defaultValue(Integer.MAX_VALUE)
            .withDescription("actor 线程池队列大小.");

    /**
     * actor 系统吞吐量:每次拉取消息的数量.
     */
    public static final ConfigOption<Integer> ACTOR_MSG_POLL_NUM = key("master.actor.msg-poll-num")
            .intType()
            .defaultValue(100)
            .withDescription("actor 系统吞吐量:每次拉取消息的数量.");

    /**
     * actor 系统初始化失败重试最大次数.
     */
    public static final ConfigOption<Integer> ACTOR_INIT_MAX_ATTEMPTS = key("master.actor.init-max-attempts")
            .intType()
            .defaultValue(1)
            .withDescription("actor 系统初始化失败重试最大次数.");

    //endregion
    //region: event线程池配置

    /**
     * event 线程池core size.
     */
    public static final ConfigOption<Integer> EVENT_POOL_CORE_SIZE = key("master.event.pool.core-size")
            .intType()
            .defaultValue(10)
            .withDescription("event 线程池core size.");

    /**
     * event 线程池max size.
     */
    public static final ConfigOption<Integer> EVENT_POOL_MAX_SIZE = key("master.event.pool.max-size")
            .intType()
            .defaultValue(20)
            .withDescription("event 线程池max size.");

    /**
     * event 线程池keepalive time(秒).
     */
    public static final ConfigOption<Integer> EVENT_POOL_KEEP_ALIVE_TIME = key("master.event.pool.keep-alive-time")
            .intType()
            .defaultValue(60)
            .withDescription("event 线程池keepalive time(秒).");

    /**
     * event 线程池队列大小.
     */
    public static final ConfigOption<Integer> EVENT_POOL_CAPACITY = key("master.event.pool.capacity")
            .intType()
            .defaultValue(Integer.MAX_VALUE)
            .withDescription("event 线程池队列大小.");

    /**
     * 事件保留个数.
     * 单位:个,按元素个数保留,默认15分钟一个记录=7天*24小时*4个/2
     */
    public static final ConfigOption<Integer> EVENT_RETAIN_NUM = key("master.event.retain-num")
            .intType()
            .defaultValue(336)
            .withDescription("事件保留个数,按元素个数保留,默认15分钟一个记录=7天*24小时*4个/2.");

    /**
     * 事件保留时间(毫秒).
     */
    public static final ConfigOption<Long> EVENT_RETAIN_TIME = key("master.event.retain-time")
            .longType()
            .defaultValue(7 * 24 * 60 * 60 * 1000L)
            .withDescription("事件保留时间(毫秒).");

    //endregion
    //region: 触发器线程池配置

    /**
     * trigger 线程池core size.
     */
    public static final ConfigOption<Integer> TRIGGER_POOL_CORE_SIZE = key("master.trigger.pool.core-size")
            .intType()
            .defaultValue(10)
            .withDescription("trigger 线程池core size.");

    /**
     * trigger 线程池max size.
     */
    public static final ConfigOption<Integer> TRIGGER_POOL_MAX_SIZE = key("master.trigger.pool.max-size")
            .intType()
            .defaultValue(20)
            .withDescription("trigger 线程池max size.");

    /**
     * trigger 线程池keepalive time(秒).
     */
    public static final ConfigOption<Integer> TRIGGER_POOL_KEEP_ALIVE_TIME = key("master.trigger.pool.keep-alive-time")
            .intType()
            .defaultValue(60)
            .withDescription("trigger 线程池keepalive time(秒).");

    /**
     * trigger 线程池队列大小.
     */
    public static final ConfigOption<Integer> TRIGGER_POOL_CAPACITY = key("master.trigger.pool.capacity")
            .intType()
            .defaultValue(Integer.MAX_VALUE)
            .withDescription("trigger 线程池队列大小.");

    /**
     * 预处理时间: 30分钟.
     */
    public static final ConfigOption<Long> PREPARED_MS = key("master.trigger.prepared-ms")
            .longType()
            .defaultValue(30 * 60 * 1000L)
            .withDescription("预处理时间(毫秒).");

    /**
     * 一个批次读取数.
     */
    public static final ConfigOption<Integer> BATCH_READ_COUNT = key("master.trigger.batch-read-count")
            .intType()
            .defaultValue(1000)
            .withDescription("一个批次读取数.");

    /**
     * 轮询间隔时间.
     */
    public static final ConfigOption<Long> POLL_INTERVAL = key("master.trigger.poll-interval")
            .longType()
            .defaultValue(1000L)
            .withDescription("轮询间隔时间(毫秒).");

    //endregion
    //region: caffeine 缓存容量大小

    /**
     * flow实例缓存最大大小.
     */
    public static final ConfigOption<Integer> FLOW_INSTANCE_CACHE_MAX_SIZE = key("master.cache.flow-instance-max-size")
            .intType()
            .defaultValue(1024)
            .withDescription("flow实例缓存最大大小.");

    /**
     * task 实例缓存最大大小.
     */
    public static final ConfigOption<Integer> TASK_INSTANCE_CACHE_MAX_SIZE = key("master.cache.task-instance-max-size")
            .intType()
            .defaultValue(1024 * 10)
            .withDescription("task实例缓存最大大小.");

    /**
     * event 实例缓存最大大小.
     */
    public static final ConfigOption<Integer> EVENT_INSTANCE_CACHE_MAX_SIZE = key("master.cache.event-instance-max-size")
            .intType()
            .defaultValue(1024 * 10)
            .withDescription("event实例缓存最大大小.");

    //endregion

    private MasterConfigOptions() {
    }
}

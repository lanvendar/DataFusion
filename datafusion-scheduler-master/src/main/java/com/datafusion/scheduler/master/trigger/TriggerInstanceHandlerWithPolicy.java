package com.datafusion.scheduler.master.trigger;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.trigger.core.TriggerDelayQueue;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

/**
 * 调度实例生成器,生成按调度时间排序的某个周期内的调度实例缓存.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/3/16
 * @since 2023/3/16
 */
@Slf4j
public class TriggerInstanceHandlerWithPolicy implements TriggerInstanceHandler {

    /**
     * 当前某个调度周期内实例Map.
     */
    private final ConcurrentSkipListMap<Long, HashSet<TriggerInstance>> scheduleInstanceMap = new ConcurrentSkipListMap<>();

    /**
     * 条件不满足时，等待检查间隔时间.
     */
    private static final long WAITING_INTERVAL = 30000L;

    /**
     * 内部的等待队列.
     */
    private final TriggerDelayQueue<TriggerInstance> waitingDelayQueue = new TriggerDelayQueue<>();

    /**
     * 调度延迟队列.
     */
    private final TriggerDelayQueue<TriggerInstance> queue;

    /**
     * 调度延迟队列出队间隔时间.
     */
    private final long pollInterval;

    /**
     * 预处理时间: 30分钟.
     */
    private final long preparedMs;

    /**
     * 一个批次读取数.
     */
    private final int batchReadCount;

    /**
     * 调度策略接口.
     */
    @Getter
    private final SchedulerTrigger schedulerTrigger;

    /**
     * 调度存储实现类.
     */
    private final TriggerStorage triggerStorage;

    /**
     * 构造函数.
     *
     * @param schedulerTrigger 策略接口
     * @param triggerStorage   调度存储实现
     * @param options          调度统一配置
     */
    public TriggerInstanceHandlerWithPolicy(SchedulerTrigger schedulerTrigger, TriggerStorage triggerStorage, Options options) {
        this.schedulerTrigger = schedulerTrigger;
        this.triggerStorage = triggerStorage;
        //调度延迟触发队列
        this.queue = new TriggerDelayQueue<>();
        this.pollInterval = options.get(MasterConfigOptions.POLL_INTERVAL);
        this.preparedMs = options.get(MasterConfigOptions.PREPARED_MS);
        this.batchReadCount = options.get(MasterConfigOptions.BATCH_READ_COUNT);
    }

    /**
     * 添加flow调度信息. 注意：在特殊情况下可能重复生成相同schedule id， 如： 1.添加schedule info和直接添加schedule instance出现重复 2.已经生成的schedule
     * instance被取消调度（此时cache中存在，只是调度flag设置为false）后，添加新的schedule info 如果限制schedule id在cache中唯一，那么需要考虑如何找到被取消调度的schedule
     * instance，然后再删除。实现比较复杂。 考虑在dispatch thread和fetch thread中通过instance状态判断来处理重复，此要求两个thread为单线程处理.
     *
     * @param triggerInfo 调度器对象
     * @param baseTime    基准计算时间
     * @param included    是否包含基准时间
     */
    @Override
    public void generateInstance(TriggerInfo triggerInfo, long baseTime, boolean included) {
        long scheduleTime = triggerInfo.calScheduleTime(baseTime, included);
        log.debug("基准时间=[{}]生成scheduleTime=[{}]", baseTime, scheduleTime);

        if (scheduleTime < 0) {
            log.warn("schedule info没有下一次调度，不会生成新的schedule instance");
            //return null;
        } else {
            TriggerInstance triggerInstance = new TriggerInstance();
            // 生成触发器的实例的 id，也是流程实例的 id,也是流程 actor 的 id
            triggerInstance.setInstanceId(genInstanceId(triggerInfo, scheduleTime));
            triggerInstance.setScheduleTime(scheduleTime);
            triggerInstance.setPayloadId(triggerInfo.getPayloadId());
            triggerInstance.setVersion(triggerInfo.getVersion());
            this.addCache(triggerInstance);
            log.debug("生成ScheduleInstance={}", triggerInstance);
            //return scheduleInstance;
        }
    }

    /**
     * 生成触发器的实例的 id，也是流程实例的 id,也是流程 actor 的 id.
     *
     * @param triggerInfo  触发器信息
     * @param scheduleTime 调度时间
     * @return 触发器的实例的 id
     */
    private String genInstanceId(TriggerInfo triggerInfo, long scheduleTime) {
        //根据 payloadId(flowId) + "_" + scheduleTime + "_" + version 生成 uuid
        return UUID.nameUUIDFromBytes((//
                triggerInfo.getPayloadId() + SystemConstant.UNDER_LINE //
                        + scheduleTime + SystemConstant.UNDER_LINE //
                        + triggerInfo.getVersion()) //
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 添加调度实例到缓存.
     *
     * @param triggerInstance 调度实例
     */
    @Override
    public void addCache(TriggerInstance triggerInstance) {
        HashSet<TriggerInstance> set = new HashSet<>();
        set.add(triggerInstance);
        set = scheduleInstanceMap.putIfAbsent(triggerInstance.getScheduleTime(), set);
        if (set != null) {
            synchronized (set) {
                if (!set.isEmpty()) {
                    set.add(triggerInstance);
                } else {
                    addCache(triggerInstance);
                }
            }
        }
    }

    @Override
    public void enqueue(TriggerInstance instance) {
        this.queue.enqueue(instance, instance.getScheduleTime());
    }

    @Override
    public List<TriggerInstance> dequeue() {
        return this.queue.dequeue(pollInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试将等待的Schedule instance加入cache中.
     */
    private void waitToCache() {
        // 导出所有可用的ScheduleInstance加到缓存中
        List<TriggerInstance> waitingInstances = waitingDelayQueue.tryDrain();
        if (CollectionUtil.isNotEmpty(waitingInstances)) {
            for (TriggerInstance wi : waitingInstances) {
                this.addCache(wi);
            }
        }
    }

    /**
     * 根据时间和数量抓取下一批的 ScheduleInstance.
     *
     * @return 调度实例列表
     */
    @Override
    public List<TriggerInstance> fetchCache() {
        // 从等待队列中加入缓存
        this.waitToCache();
        List<TriggerInstance> resultList = new ArrayList<>();

        long futureTime = System.currentTimeMillis() + preparedMs;
        while (resultList.size() < batchReadCount && !scheduleInstanceMap.isEmpty()) {
            // 若缓存中无调度数据或没有大于当前时间的缓存数据,则退出循环,等待下次执行线程
            Long headKey = scheduleInstanceMap.firstKey();
            if (headKey == null || headKey >= futureTime) {
                break;
            }
            // 若有大于当前时间的缓存数据
            Set<TriggerInstance> instanceInfoSet = scheduleInstanceMap.remove(headKey);
            if (CollectionUtil.isNotEmpty(instanceInfoSet)) {
                synchronized (instanceInfoSet) {
                    for (TriggerInstance triggerInstance : instanceInfoSet) {
                        try {
                            // 检查调度策略,若无策略请返回 0
                            long waitMillis = onPolicyWait(triggerInstance);
                            if (waitMillis < 0) {
                                continue;
                            } else if (waitMillis == 0) {
                                resultList.add(triggerInstance);
                            } else {
                                waitingDelayQueue.enqueue(triggerInstance, waitMillis);
                            }
                        } catch (Exception e) {
                            log.error("检查调度策略失败", e);
                            waitingDelayQueue.enqueue(triggerInstance, WAITING_INTERVAL);
                        }
                    }
                    instanceInfoSet.clear();
                }
            } else {
                log.warn("不可能发生");
            }
        }

        return resultList;
    }

    /**
     * 判断策略是否满足实例化要求.
     *
     * @param triggerInstance 调度实例对象
     * @return 返回策略等待时间
     */
    private long onPolicyWait(TriggerInstance triggerInstance) {
        //1.判断实例是否已经生成过,生成过代表已经走过此策略,则直接调用
        if (null != triggerStorage.getTriggerInstance(triggerInstance.getInstanceId())) {
            return 0;
        }
        //2.判断调度版本和标识
        if (!this.checkScheduleAvailable(triggerInstance)) {
            return -1;
        }
        //3.判断策略
        //获取上一次执行的flow instance
        TriggerInstance lastInstance = triggerStorage.getLastTriggerInstance(
                triggerInstance.getPayloadId(), triggerInstance.getVersion());
        StatusEnum lastInsStatusEnum = (lastInstance != null) ? lastInstance.getState() : null;
        if (lastInsStatusEnum == null) {
            log.info("没有上一次执行的实例，被调度对象的id={}", triggerInstance.getPayloadId());
            return 0;
        } else {
            log.debug("获取上一次执行实例的状态为state={}", lastInsStatusEnum.getStateType());
            //根据调度策略做不同处理
            TriggerInfo triggerInfo = triggerStorage.getTriggerInfo(triggerInstance.getPayloadId());
            switch (triggerInfo.getTriggerPolicy()) {
                case EXECUTE_ONCE:
                    //上面已判断是否有上一次执行的实例,故此处直接返回-1不调度
                    log.debug("已经存在上一次执行的实例，不再进行调度");
                    return -1;
                case SERIAL_WAIT:
                    log.debug("调度策略为顺序执行");
                    if (lastInsStatusEnum.isSuccess()) {
                        log.debug("上一次执行状态已成功，则添加到返回结果");
                        return 0;
                    } else {
                        log.debug("上一次执行状态未成功，则添加到等待队列");
                        return System.currentTimeMillis() + WAITING_INTERVAL;
                    }
                case DISCARD_NEW:
                    log.debug("调度策略为丢弃最新");
                    if (lastInsStatusEnum.isSuccess()) {
                        log.debug("上一次执行状态已成功，则添加到返回结果");
                        return 0;
                    } else {
                        //设置等待下一次周期
                        if (triggerInstance.getScheduleTime() <= System.currentTimeMillis()) {
                            long nextTerm = triggerInfo.calScheduleTime(triggerInstance.getScheduleTime(), false);
                            triggerInstance.setScheduleTime(nextTerm);
                            log.debug("刷新下一次调度时间，nextTerm={}", nextTerm);
                        }
                        //判断等待时长是否超过等待周期
                        log.debug("等待本次次调度时间，ScheduleTime={}", triggerInstance.getScheduleTime());
                        if (triggerInstance.getScheduleTime() > (System.currentTimeMillis() + WAITING_INTERVAL)) {
                            return System.currentTimeMillis() + WAITING_INTERVAL;
                        } else {
                            return triggerInstance.getScheduleTime();
                        }
                    }
                case DISCARD_OLD:
                    log.debug("调度策略为覆盖执行");
                    if (lastInsStatusEnum.isSuccess()) {
                        log.debug("上一次执行状态已成功，则添加到返回结果");
                        return 0;
                    } else {
                        log.debug("上一次执行状态未成功，先停止上一次的flow instance，再添加到返回结果");
                        //TODO 此处可能延迟到真实时间才能去做初始化操作,为了 DISCARD_OLD 枚举不写在 SchedulerDispatchThread 分发的代码中
                        if (triggerInstance.getScheduleTime() < System.currentTimeMillis()) {
                            /*
                             * TODO
                             * 覆盖执行需要之前的实例完成（成功、强制成功），未完成时需要有一个方式关闭（close）流程
                             * 让其未来不能被重启，同时也满足取消发布时所有实例是最终状态的要求。
                             * 考虑给流程增加close动作和closing|closed状态。
                             * 20230816:可以考虑延迟停止或强制停止的功能,此处先抛出一个抽象方法。
                             */
                            schedulerTrigger.killDelay(triggerInstance.getPayloadId(), preparedMs - WAITING_INTERVAL);
                            return 0;
                        } else {
                            return System.currentTimeMillis() + WAITING_INTERVAL;
                        }
                    }
                case PARALLEL:
                default:
                    log.debug("调度策略为并行或者没设置，则添加到返回结果");
                    return 0;
            }
        }
    }

    @Override
    public boolean checkScheduleAvailable(TriggerInstance triggerInstance) {
        TriggerInfo triggerInfo = triggerStorage.getTriggerInfo(triggerInstance.getPayloadId());
        if (triggerInfo == null) {
            log.error("调度信息不存在,payloadId=[{}]", triggerInstance.getPayloadId());
            return false;
        }
        if (!triggerInfo.isScheduleFlag() || !triggerInfo.getVersion().equals(triggerInstance.getVersion())) {
            log.warn("当前调度标识ScheduleFlag=[{}]取消调度;或者调度版本与实例的版本不一致,调度版本version=[{}],"
                            + "实例version=[{}]", triggerInfo.isScheduleFlag(), triggerInfo.getVersion(),
                    triggerInstance.getVersion());
            return false;
        }
        return true;
    }

    @Override
    public StatusEnum getScheduleInstanceState(String scheduleInsId) {
        TriggerInstance instance = triggerStorage.getTriggerInstance(scheduleInsId);
        return (instance != null) ? instance.getState() : null;
    }

    @Override
    public void saveLastScheduleInstance(TriggerInstance lastTriggerInstance) {
        triggerStorage.saveTriggerInstance(lastTriggerInstance);
    }

    @Override
    public void saveTriggerInstance(TriggerInstance triggerInstance) {
        triggerStorage.saveTriggerInstance(triggerInstance);
    }

    @Override
    public TriggerInfo getTriggerInfo(String payloadId) {
        return triggerStorage.getTriggerInfo(payloadId);
    }
}

package com.datafusion.scheduler.worker;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.scheduler.worker.model.Worker;
import com.datafusion.scheduler.worker.storage.WorkerStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 工作节点管理.
 *
 * @author lanvender
 * @version 3.7.4, 2024/11/19
 * @since 3.7.4, 2024/11/19
 */
@Slf4j
public class WorkerManager implements WorkerListener {

    // region 成员属性

    /**
     * 保存了插件与查找对应工作节点次数的关系map，给ROUND_ROBIN负载均衡测试使用.
     */
    private final ConcurrentHashMap<String, AtomicLong> pluginLookupCountMap = new ConcurrentHashMap<>();

    /**
     * 随机负载使用.
     */
    private final Random random = new Random();

    /**
     * 工作节点存储服务.
     */
    private final WorkerStorage storage;

    // endregion

    // region 构造函数

    /**
     * 构造函数.
     *
     * @param storage 工作节点存储服务
     */
    public WorkerManager(WorkerStorage storage) {
        this.storage = storage;
        inactiveAllNodes();
    }

    /**
     * 设置全部节点不可用.
     */
    private void inactiveAllNodes() {
        if (null == storage) {
            return;
        }

        List<Worker> workers = storage.getWorkers();
        if (CollectionUtil.isNotEmpty(workers)) {
            workers.stream().filter(Worker::isAlive).forEach(w -> {
                Worker worker = new Worker();
                worker.setIp(w.getIp());
                worker.setPort(w.getPort());
                worker.setHostName(w.getHostName());
                worker.setPluginTypes(w.getPluginTypes());
                onInactive(worker);
            });
        }
    }

    // endregion

    // region 工作节点上下线监听.

    /**
     * 节点上线.
     *
     * @param workerNode 节点
     */
    @Override
    public void onActive(Worker workerNode) {
        log.info("节点上线：{}", workerNode);
        Worker worker = storage.getWorker(workerNode.getHostName(), workerNode.getPort());
        if (null == worker) {
            worker = new Worker();
            worker.setId(UUID.randomUUID().toString());
        }

        worker.setIp(workerNode.getIp());
        worker.setPort(workerNode.getPort());
        worker.setStatus(Worker.STATUS_UP);
        worker.setHostName(workerNode.getHostName());
        worker.setPluginTypes(workerNode.getPluginTypes());

        storage.updateWorker(worker);
    }

    /**
     * 节点下线.
     *
     * @param node 节点
     */
    @Override
    public void onInactive(Worker node) {
        log.info("节点下线：{}", node);
        Worker worker = storage.getWorker(node.getHostName(), node.getPort());
        if (null != worker && worker.getStatus().equals(Worker.STATUS_UP)) {
            worker.setStatus(Worker.STATUS_DOWN);
            storage.updateWorker(worker);

            // TODO failOver.
        }
    }
    // endregion

    /**
     * 根据工作节点ID获取工作节点.
     *
     * @param workerId 工作节点ID
     * @return 工作节点
     */
    public Worker getWorker(String workerId) {
        return storage.getWorker(workerId);
    }

    /**
     * 默认获取支持指定插件的工作节点. 注意：不管由于并发还是节点实际状态不匹配，都有可能返回的是非活跃的工作节点.
     *
     * @param pluginType 插件类型
     * @return 工作节点
     */
    public Worker lookupWorker(String pluginType) {
        return lookupWorker(pluginType, LoadBalanceStrategy.RANDOM);
    }

    /**
     * 根据负载策略选择工作节点.
     *
     * @param pluginType 组件类型
     * @param lbStrategy 负载策略
     * @return 工作节点·
     */
    private Worker lookupWorker(String pluginType, LoadBalanceStrategy lbStrategy) {
        List<Worker> workers = storage.getWorkers();
        if (CollectionUtil.isNotEmpty(workers)) {
            workers = workers.stream().filter(Worker::isAlive)
                    .filter(worker -> worker.getPluginTypes().contains(pluginType))
                    .collect(Collectors.toList());
        }

        if (CollectionUtil.isEmpty(workers)) {
            return null;
        }

        Worker worker;
        switch (lbStrategy) {
            case ROUND_ROBIN:
                AtomicLong lookupCount = pluginLookupCountMap.compute(pluginType, (k, v) -> {
                    if (v == null) {
                        v = new AtomicLong(0);
                    }
                    v.incrementAndGet();
                    return v;
                });
                worker = workers.get((int) ((lookupCount.get() - 1) % workers.size()));
                break;
            case RANDOM:
                int index = random.nextInt(workers.size());
                return workers.get(index);
            case BEST_AVAILABLE:
            default:
                worker = workers.get(0);
        }

        return worker;
    }

    enum LoadBalanceStrategy {

        /**
         * RoundRobin.
         */
        ROUND_ROBIN,
        /**
         * Random.
         */
        RANDOM,
        /**
         * best available.
         */
        BEST_AVAILABLE;
    }
}

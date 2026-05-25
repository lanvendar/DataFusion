package com.datafusion.manager.scheduler.master.task;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.scheduler.exception.SchedulerException;
import com.datafusion.scheduler.exception.SchedulerExceptionCode;
import com.datafusion.scheduler.master.task.TaskExecutor;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.WorkerManager;
import com.datafusion.scheduler.worker.model.TaskRequest;
import com.datafusion.scheduler.worker.model.Worker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

/**
 * LoadBalanceExecutor.
 *
 * @author david
 * @version 3.6.4, 2024/10/29
 * @since 3.6.4, 2024/10/29
 */
@Slf4j
public class HttpTaskExecutor implements TaskExecutor {

    // region 成员属性

    /**
     * 执行任务的uri.
     */
    public static final String RUN_TASK_URL = "http://host:port/internal/schedule/runTask";

    /**
     * 停止任务的uri.
     */
    public static final String STOP_TASK_URL = "http://host:port/internal/schedule/stopTask";

    /**
     * 完成任务的uri.
     */
    public static final String FINISH_TASK_URL = "http://host:port/internal/schedule/finishTask";

    /**
     * 工作节点存储服务.
     */
    private final WorkerManager manager;

    /**
     * http client.
     */
    private final CloseableHttpClient httpClient;

    /**
     * 请求配置.
     */
    private final RequestConfig config;
    // endregion

    // region 构造函数

    /**
     * 构造器，初始化http client.
     *
     * @param manager 工作节点管理服务
     */
    public HttpTaskExecutor(WorkerManager manager) {
        this(manager, 20, 10, 5000, 5000, 10000);
    }

    /**
     * 构造器，初始化http client.
     *
     * @param manager                  工作节点管理服务
     * @param maxConcurrent            客户端总并行链接最大数
     * @param maxPerRoute              每个主机的最大并行链接数
     * @param connectionTimeout        连接超时时间
     * @param connectionRequestTimeout 从线程池中获取线程超时时间
     * @param socketTimeout            数据超时时间
     */
    public HttpTaskExecutor(WorkerManager manager, int maxConcurrent, int maxPerRoute, int connectionTimeout, int connectionRequestTimeout,
                            int socketTimeout) {
        this.manager = manager;
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        //客户端总并行链接最大数
        connectionManager.setMaxTotal(maxConcurrent);
        //每个主机的最大并行链接数
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);

        httpClient = HttpClients.custom().setConnectionManager(connectionManager)
                // .addInterceptorFirst(new HttpClientLogInterceptor())
                .build();

        config = RequestConfig.custom().setConnectTimeout(connectionTimeout).setConnectionRequestTimeout(connectionRequestTimeout)
                .setSocketTimeout(socketTimeout).build();
    }
    // endregion

    // region 接口实现

    /**
     * 提交任务.
     *
     * @param taskIns task实例
     * @return task执行结果
     */
    @Override
    public TaskResult runTask(TaskInstance taskIns) throws SchedulerException {
        Worker worker = getLastExecutedWorker(taskIns);
        if (null == worker) {
            PluginData pluginData = taskIns.getPluginData();
            worker = manager.lookupWorker(pluginData.getPluginType());
        }

        if (null == worker || !worker.isAlive()) {
            log.warn("[{}] - 任务实例无可用节点提交任务", taskIns.getInstanceId());
            throw new SchedulerException(SchedulerExceptionCode.CANNOT_FIND_RESOURCE);
        }

        try {
            TaskResult response = requestToWorker(RUN_TASK_URL, worker, taskIns);
            // 设置任务实例对应的工作节点ID.
            TaskResult taskResult = taskIns.getTaskResult();
            if (taskResult != null) {
                taskResult.setWorkerId(worker.getId());
            }
            return response;
        } catch (Exception e) {
            log.error("[{}] - 发送run task请求失败", taskIns.getInstanceId(), e);
            throw new SchedulerException("http error" + HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 停止任务.
     *
     * @param taskIns task任务实例
     * @return task执行结果
     */
    @Override
    public TaskResult stopTask(TaskInstance taskIns) throws SchedulerException {
        Worker worker = getLastExecutedWorker(taskIns);
        if (null == worker || !worker.isAlive()) {
            // TODO 停止任务时是否存在跨节点停止的情况?
            log.warn("[{}] - 工作节点不可用, 无法停止任务", taskIns.getInstanceId());
            throw new SchedulerException(SchedulerExceptionCode.CANNOT_FIND_RESOURCE);
        }

        try {
            return requestToWorker(STOP_TASK_URL, worker, taskIns);
        } catch (Exception e) {
            log.error("[{}] - 发送stop task请求失败", taskIns.getInstanceId(), e);
            throw new SchedulerException("http error" + HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public TaskResult killTask(TaskInstance taskIns) throws SchedulerException {
        return null;
    }

    /**
     * 完成任务.
     *
     * @param taskIns task任务实例
     * @return task执行结果
     */
    @Override
    public TaskResult finishTask(TaskInstance taskIns) throws SchedulerException {
        Worker worker = getLastExecutedWorker(taskIns);
        if (null == worker || !worker.isAlive()) {
            log.warn("[{}] - 工作节点不可用, 无法正常完成任务", taskIns.getInstanceId());
            throw new SchedulerException(SchedulerExceptionCode.CANNOT_FIND_RESOURCE);
        }

        try {
            log.info("finish Task {}", taskIns.getInstanceId());
            return requestToWorker(FINISH_TASK_URL, worker, taskIns);
        } catch (Exception e) {
            log.error("[{}] - 发送finish task请求失败", taskIns.getInstanceId(), e);
            throw new SchedulerException("http error" + HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    // endregion

    // region 通用请求

    /**
     * 获取上一次提交请求的工作节点.
     *
     * @param taskIns 任务实例
     * @return 工作节点
     */
    private Worker getLastExecutedWorker(TaskInstance taskIns) {
        // 任务已经在工作节点上执行,那么使用当时的工作节点
        TaskResult taskResult = taskIns.getTaskResult();
        if (taskResult != null && StringUtils.isNotBlank(taskResult.getWorkerId())) {
            return manager.getWorker(taskResult.getWorkerId());
        }

        return null;
    }

    /**
     * 向工作节点发送请求.
     *
     * @param url     请求URL模板
     * @param worker  工作节点
     * @param taskIns 任务实例
     * @return 请求结果
     * @throws Exception 请求异常
     */
    private TaskResult requestToWorker(String url, Worker worker, TaskInstance taskIns) throws Exception {
        url = url.replaceFirst("host", worker.getIp()).replaceFirst("port", worker.getPort().toString());
        String body = createRequestBody(taskIns);
        log.info("requestToWorker url {} ...", url);
        String send = this.send(url, body);
        Result<TaskResult> result = null;
        if (StringUtils.isNotBlank(send)) {
            result = JacksonUtils.tryStr2Bean(send, new TypeReference<Result<TaskResult>>() {
            });
        }

        TaskResult response;
        if (result != null && result.getCode().equals(ErrorCodeEnum.SUCCESS.getCode())) {
            response = result.getData();
        } else {
            log.warn("[{}] - 发送请求给工作节点失败,错误码:{}, 错误信息:{}", taskIns.getInstanceId(), result.getCode(), result.getErrorMsg());
            response = TaskResult.builder().result(result.getErrorMsg()).build();
        }
        return response;
    }

    /**
     * 创建请求参数.
     *
     * @param taskIns 任务实例
     * @return 请求参数
     */
    private String createRequestBody(TaskInstance taskIns) {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId(taskIns.getFlowInstanceId());
        request.setTaskInstanceId(taskIns.getInstanceId());
        request.setTaskName(taskIns.getTaskName());
        // TODO 替换任务定义.
        request.setDefinition(taskIns.getTaskData().getParams());

        TaskResult taskResult = taskIns.getTaskResult();
        if (null != taskResult) {
            request.setAppId(taskResult.getAppId());
        }

        PluginData pluginData = taskIns.getPluginData();
        request.setPluginType(pluginData.getPluginType());
        request.setPluginParam(pluginData.getPluginParam());
        request.setTaskState(taskIns.getState());

        return JacksonUtils.tryObj2Str(request);
    }

    /**
     * 发送指令给agent.
     *
     * @param url   url地址
     * @param param 参数
     * @return 字符串结果
     * @throws Exception Http异常
     */
    private String send(String url, String param) throws Exception {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(config);
        httpPost.setHeader("Content-Type", "application/json");

        StringEntity se = new StringEntity(param, StandardCharsets.UTF_8);
        httpPost.setEntity(se);

        CloseableHttpResponse response = httpClient.execute(httpPost);

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            EntityUtils.consume(response.getEntity());
            throw new SchedulerException("http error" + HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        HttpEntity resEntity = response.getEntity();
        //关闭响应
        if (resEntity != null) {
            String sRet = EntityUtils.toString(resEntity, StandardCharsets.UTF_8);
            EntityUtils.consume(response.getEntity());
            return sRet;
        } else {
            EntityUtils.consume(response.getEntity());
            return null;
        }
    }
    // endregion

}

package com.datafusion.agent.runtime;

import com.datafusion.scheduler.model.Worker;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 运行状态.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Data
public class AgentRuntimeState {

    /**
     * 当前 worker 信息.
     */
    private Worker worker;

    /**
     * 是否已注册并可调度.
     */
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * 插件类型.
     */
    private List<String> pluginTypes = Collections.emptyList();

    /**
     * 判断是否就绪.
     *
     * @return 是否就绪
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * 设置就绪状态.
     *
     * @param value 是否就绪
     */
    public void setReady(boolean value) {
        ready.set(value);
    }
}

package com.datafusion.plugin.kafka.json.runtime;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.RuntimeConfig;
import com.datafusion.plugin.kafka.json.core.enums.CheckpointMode;
import com.datafusion.plugin.kafka.json.core.enums.DeploymentMode;
import com.datafusion.plugin.kafka.json.core.enums.ExecutionMode;
import com.datafusion.plugin.kafka.json.core.enums.RestartStrategyType;
import com.datafusion.plugin.kafka.json.core.enums.StateBackendType;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Flink runtime 配置器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class FlinkRuntimeConfigurer {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlinkRuntimeConfigurer.class);

    /**
     * 应用 runtime 配置.
     *
     * @param env Flink 执行环境
     * @param runtime runtime 配置
     */
    public void configure(StreamExecutionEnvironment env, RuntimeConfig runtime) {
        RuntimeConfig actual = runtime == null ? new RuntimeConfig() : runtime;
        env.setParallelism(valueOrDefault(actual.parallelism, 1));
        env.setRuntimeMode(toRuntimeMode(ExecutionMode.parse(actual.executionMode)));
        Configuration configuration = new Configuration();
        configureStateBackend(configuration, actual);
        configureRestartStrategy(configuration, actual);
        configureCheckpointStorage(configuration, actual);
        env.configure(configuration);
        configureCheckpoint(env, actual);

        DeploymentMode deploymentMode = DeploymentMode.parse(actual.deploymentMode);
        LOGGER.info("Flink runtime configured, deploymentMode={}, executionMode={}, parallelism={}",
                deploymentMode, actual.executionMode, actual.parallelism);
    }

    private void configureCheckpoint(StreamExecutionEnvironment env, RuntimeConfig runtime) {
        long intervalMs = valueOrDefault(runtime.checkpointIntervalMs, 60000L);
        env.enableCheckpointing(intervalMs, toCheckpointingMode(CheckpointMode.parse(runtime.checkpointMode)));
        env.getCheckpointConfig().setCheckpointTimeout(valueOrDefault(runtime.checkpointTimeoutMs, 600000L));
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(valueOrDefault(runtime.maxConcurrentCheckpoints, 1));
    }

    private void configureCheckpointStorage(Configuration configuration, RuntimeConfig runtime) {
        if (!TextUtils.isBlank(runtime.checkpointStorage)) {
            configuration.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
            configuration.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, runtime.checkpointStorage);
        }
    }

    private void configureStateBackend(Configuration configuration, RuntimeConfig runtime) {
        StateBackendType backendType = StateBackendType.parse(runtime.stateBackend);
        if (backendType == StateBackendType.ROCKSDB) {
            configuration.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
            return;
        }
        configuration.set(StateBackendOptions.STATE_BACKEND, "hashmap");
    }

    private void configureRestartStrategy(Configuration configuration, RuntimeConfig runtime) {
        RestartStrategyType strategyType = RestartStrategyType.parse(runtime.restartStrategy);
        if (strategyType == RestartStrategyType.NO_RESTART) {
            configuration.set(RestartStrategyOptions.RESTART_STRATEGY,
                    org.apache.flink.configuration.RestartStrategyOptions.RestartStrategyType.NO_RESTART_STRATEGY.getMainValue());
            return;
        }
        if (strategyType == RestartStrategyType.FAILURE_RATE) {
            configuration.set(RestartStrategyOptions.RESTART_STRATEGY,
                    org.apache.flink.configuration.RestartStrategyOptions.RestartStrategyType.FAILURE_RATE.getMainValue());
            configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_MAX_FAILURES_PER_INTERVAL,
                    valueOrDefault(runtime.restartAttempts, 3));
            configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_FAILURE_RATE_INTERVAL,
                    Duration.ofMinutes(5));
            configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_DELAY,
                    Duration.ofMillis(valueOrDefault(runtime.restartDelayMs, 10000L)));
            return;
        }
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY,
                org.apache.flink.configuration.RestartStrategyOptions.RestartStrategyType.FIXED_DELAY.getMainValue());
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, valueOrDefault(runtime.restartAttempts, 3));
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY,
                Duration.ofMillis(valueOrDefault(runtime.restartDelayMs, 10000L)));
    }

    private RuntimeExecutionMode toRuntimeMode(ExecutionMode mode) {
        return mode == ExecutionMode.BATCH ? RuntimeExecutionMode.BATCH : RuntimeExecutionMode.STREAMING;
    }

    private CheckpointingMode toCheckpointingMode(CheckpointMode mode) {
        return mode == CheckpointMode.AT_LEAST_ONCE ? CheckpointingMode.AT_LEAST_ONCE : CheckpointingMode.EXACTLY_ONCE;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long valueOrDefault(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }
}

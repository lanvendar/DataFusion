package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基于文件的 Agent 执行状态记录器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/3
 * @since 1.0.0
 */
@Slf4j
public class FileAgentExecutionStatusRecorder implements AgentExecutionStatusRecorder {

    /**
     * 日期格式.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * agent 运行状态.
     */
    private final AgentRuntimeState runtimeState;

    /**
     * 构造函数.
     *
     * @param properties   agent 配置
     * @param runtimeState agent 运行状态
     */
    public FileAgentExecutionStatusRecorder(AgentProperties properties, AgentRuntimeState runtimeState) {
        this.properties = properties;
        this.runtimeState = runtimeState;
    }

    @Override
    public void record(AgentExecutionStatusRecord record) {
        if (record == null || record.getExecutionId() == null) {
            return;
        }
        try {
            Path executionDir = executionStatusDir(record.getFlowInstanceId(), record.getExecutionId());
            Files.createDirectories(executionDir);
            Files.writeString(executionDir.resolve("taskStatus.log"), statusLine(record) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(executionDir.resolve(record.getExecutionId() + ".state"), stateContent(record),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("记录执行状态文件失败, executionId={}", record.getExecutionId(), e);
        }
    }

    private Path executionStatusDir(String flowInstanceId, String executionId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getModules(), properties.getStorage().getTaskStatusDir(), date,
                safePath(flowInstanceId), safePath(executionId));
    }

    private String statusLine(AgentExecutionStatusRecord record) {
        return "appid:" + safeText(record.getAppId()) + "|pid:" + safeText(record.getPid())
                + "|workId:" + safeText(resolveWorkId(record)) + "|status:" + safeText(record.getStatus());
    }

    private String stateContent(AgentExecutionStatusRecord record) {
        return "executionId:" + safeText(record.getExecutionId()) + System.lineSeparator()
                + "flowInstanceId:" + safeText(record.getFlowInstanceId()) + System.lineSeparator()
                + "appId:" + safeText(record.getAppId()) + System.lineSeparator()
                + "pid:" + safeText(record.getPid()) + System.lineSeparator()
                + "workId:" + safeText(resolveWorkId(record)) + System.lineSeparator()
                + "status:" + safeText(record.getStatus()) + System.lineSeparator()
                + "result:" + safeText(record.getResult());
    }

    private String resolveWorkId(AgentExecutionStatusRecord record) {
        if (record.getWorkId() != null) {
            return record.getWorkId();
        }
        return runtimeState.getWorker() == null ? "" : runtimeState.getWorker().getId();
    }

    private String safePath(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}

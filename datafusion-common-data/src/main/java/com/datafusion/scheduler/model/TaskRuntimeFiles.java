package com.datafusion.scheduler.model;

import java.nio.file.Path;

/**
 * 任务运行目录标准文件.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
public final class TaskRuntimeFiles {

    /**
     * 标准输出日志.
     */
    public static final String STDOUT_LOG = "stdout.log";

    /**
     * 标准错误日志.
     */
    public static final String STDERR_LOG = "stderr.log";

    /**
     * 状态流水日志.
     */
    public static final String STATE_LOG = "state.log";

    private TaskRuntimeFiles() {
    }

    /**
     * 标准输出日志路径.
     *
     * @param workDir 任务运行目录
     * @return 标准输出日志路径
     */
    public static Path stdoutLog(Path workDir) {
        return resolve(workDir, STDOUT_LOG);
    }

    /**
     * 标准错误日志路径.
     *
     * @param workDir 任务运行目录
     * @return 标准错误日志路径
     */
    public static Path stderrLog(Path workDir) {
        return resolve(workDir, STDERR_LOG);
    }

    /**
     * 状态流水日志路径.
     *
     * @param workDir 任务运行目录
     * @return 状态流水日志路径
     */
    public static Path stateLog(Path workDir) {
        return resolve(workDir, STATE_LOG);
    }

    private static Path resolve(Path workDir, String fileName) {
        if (workDir == null) {
            return null;
        }
        return workDir.resolve(fileName);
    }
}

package com.datafusion.agent.runtime.worker.plugin.datax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * DataX job file service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxJobFileService {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Resolve local job file.
     *
     * @param param execution param
     * @return job file
     */
    public Path resolveJobFile(DataxExecutionParam param) {
        try {
            Files.createDirectories(param.getWorkDir());
            JsonNode jobJson = param.getJobJson();
            if (jobJson != null) {
                Path jobFile = param.getWorkDir().resolve(safeFileName(param.getJobName(), "taskData.jobName"));
                String content = jobJson.isTextual() ? jobJson.asText() : OBJECT_MAPPER.writeValueAsString(jobJson);
                Files.writeString(jobFile, content, StandardCharsets.UTF_8);
                applyPermissions(jobFile, param.getWriteJobFilePermissions());
                return jobFile;
            }
            if (!isBlank(param.getJobPath())) {
                return Path.of(param.getJobPath());
            }
            if (!isBlank(param.getJobFileName()) && !isBlank(param.getResourcesRoot())) {
                return Path.of(param.getResourcesRoot(), "job", safeFileName(param.getJobFileName(),
                        "taskData.jobFileName"));
            }
            throw new IllegalArgumentException("DataX job文件不存在");
        } catch (Exception e) {
            throw new IllegalStateException("准备DataX job文件失败: " + e.getMessage(), e);
        }
    }

    private void applyPermissions(Path file, String permissionText) {
        if (isBlank(permissionText)) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = new HashSet<>();
            for (String item : permissionText.split(",")) {
                if (!isBlank(item)) {
                    permissions.add(PosixFilePermission.valueOf(item.trim()));
                }
            }
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException e) {
            // ignore non-posix file systems
        } catch (Exception e) {
            throw new IllegalStateException("设置DataX job文件权限失败: " + file, e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeFileName(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        Path path = Path.of(value);
        if (path.isAbsolute() || path.getNameCount() != 1 || value.contains("..")) {
            throw new IllegalArgumentException(fieldName + "不能包含路径");
        }
        return value;
    }
}

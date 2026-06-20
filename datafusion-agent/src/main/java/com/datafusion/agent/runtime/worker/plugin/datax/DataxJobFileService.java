package com.datafusion.agent.runtime.worker.plugin.datax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
     * Local job file name.
     */
    private static final String LOCAL_JOB_FILE_NAME = "job.json";

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
            Path targetJobFile = param.getWorkDir().resolve(LOCAL_JOB_FILE_NAME);
            if (!isBlank(param.getJobFile())) {
                copyJobFile(Path.of(param.getJobFile()), targetJobFile);
                applyPermissions(targetJobFile, param.getWriteJobFilePermissions());
                return targetJobFile;
            }
            JsonNode jobJson = firstNode(param.getJobJson(), param.getEffectiveTaskData());
            if (jobJson != null) {
                String content = jobJson.isTextual() ? jobJson.asText() : OBJECT_MAPPER.writeValueAsString(jobJson);
                Files.writeString(targetJobFile, content, StandardCharsets.UTF_8);
                applyPermissions(targetJobFile, param.getWriteJobFilePermissions());
                return targetJobFile;
            }
            throw new IllegalArgumentException("DataX job文件不存在");
        } catch (Exception e) {
            throw new IllegalStateException("准备DataX job文件失败: " + e.getMessage(), e);
        }
    }

    private void copyJobFile(Path source, Path target) throws java.io.IOException {
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("DataX job文件不存在: " + source);
        }
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private JsonNode firstNode(JsonNode first, JsonNode second) {
        if (first != null && !first.isNull() && (!first.isObject() || !first.isEmpty())) {
            return first;
        }
        if (second != null && !second.isNull() && (!second.isObject() || !second.isEmpty())) {
            return second;
        }
        return null;
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

}

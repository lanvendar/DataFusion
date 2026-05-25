package com.datafusion.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * A collection of static utility methods to validate input.
 *
 * <p>This class is modelled after Google Guava's Preconditions class, and partly takes code from that class. We add this
 * code to the Paimon code base in order to reduce external dependencies.
 *
 * @author xufeng
 * @version 3.0, 2025/7/2
 * @since 2025/7/2
 */
@Slf4j
public class ResourceWriter {

    /**
     * Ensures that the given object reference is not null. Upon violation, a {@code
     * NullPointerException} with no message is thrown.
     *
     * @param path
     *            The type of the reference to check.
     * @param fileName
     *            The object reference
     * @param content
     *            The object reference
     * @return The object reference itself (generically typed).
     * @throws NullPointerException
     *             Thrown, if the passed reference was null.
     */
    public static Path writeToSqlResource(String path, String fileName, String content) {
        try {
            // 1. 获取 'src/main/resources' 目录的绝对路径
            // 这是一个非常健壮的方法，无论在 IDE 中运行还是在打包后都能工作
            Path projectRoot = Paths.get("").toAbsolutePath();
            log.info("Detected Project Root Directory: " + projectRoot);

            // 2. 构建到 'src/main/resources/sql' 的绝对路径
            Path sqlDirectoryPath = projectRoot.resolve(path);

            // 3. 确保 'sql' 目录存在，如果不存在就创建它
            if (Files.notExists(sqlDirectoryPath)) {
                log.info("Directory does not exist, creating: " + sqlDirectoryPath);
                Files.createDirectories(sqlDirectoryPath);
            }

            // 4. 构建最终的文件路径
            Path filePath = sqlDirectoryPath.resolve(fileName);

            // 5. 将内容写入文件。
            // - StandardOpenOption.CREATE: 如果文件不存在，就创建它。
            // - StandardOpenOption.TRUNCATE_EXISTING: 如果文件已存在，就清空它再写入（实现覆盖）。
            // - StandardOpenOption.WRITE: 指定为写入模式。
            log.info("Writing content to file: " + filePath);
            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            log.info("Successfully wrote to file: " + filePath);
            return filePath;
        } catch (Exception e) {
            System.err.println("An error occurred while writing to the resource file.");
            e.printStackTrace();
            return null;
        }
    }
}

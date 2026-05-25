package com.datafusion.manager.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/3/4
 * @since 2026/3/4
 */

public class FileUtil {

    /**
     * 有重复task_name的文件路径.
     */
    public static String duplicateFilePath = "D:\\duplicate.txt";

    /**
     * 写入文件.
     *
     * @param filePath 文件路径
     * @param content 文件内容
     */
    public static void writeFile(String filePath, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

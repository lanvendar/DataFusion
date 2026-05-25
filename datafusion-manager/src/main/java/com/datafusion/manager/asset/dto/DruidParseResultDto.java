package com.datafusion.manager.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Druid 解析结果 DTO.
 * 包含解析出的血缘边和解析日志信息
 *
 * @author xufeng
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DruidParseResultDto {

    /**
     * 解析出的血缘边列表.
     */
    private List<EdgeTableColumnDto> edges = new ArrayList<>();

    /**
     * 解析日志列表，记录 [SUCCESS] 和 [FAILURE] 信息.
     */
    private List<String> logs = new ArrayList<>();

    /**
     * 静态构建函数：创建一个表示失败的实体.
     * @param message message
     * @return entity
     */
    public static DruidParseResultDto failure(String message) {
        DruidParseResultDto dto = new DruidParseResultDto();
        // 这里的 edges 会保持为空的 ArrayList
        dto.getLogs().add("[FAILURE] " + message);
        return dto;
    }

    /**
     * 顺便建议写一个成功的构建函数，方便调用.
     * @param edges edges
     * @param message message
     * @return entity
     */
    public static DruidParseResultDto success(List<EdgeTableColumnDto> edges, String message) {
        DruidParseResultDto dto = new DruidParseResultDto();
        dto.setEdges(edges != null ? edges : new ArrayList<>());
        dto.getLogs().add("[SUCCESS] " + message);
        return dto;
    }
}

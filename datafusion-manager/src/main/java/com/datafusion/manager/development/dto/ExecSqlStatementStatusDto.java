package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开发侧SQL单条语句执行状态.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Data
@Schema(name = "ExecSqlStatementStatusDto", description = "开发侧SQL单条语句执行状态")
public class ExecSqlStatementStatusDto {

    /**
     * 在SQL脚本中的索引, 从0开始.
     */
    @Schema(name = "index", description = "语句索引")
    private Integer index;

    /**
     * 待执行的SQL文本.
     */
    @Schema(name = "sql", description = "待执行的SQL文本")
    private String sql;

    /**
     * 底层引擎实例ID, 例如MaxCompute Instance ID.
     */
    @Schema(name = "instanceId", description = "底层引擎实例ID")
    private String instanceId;

    /**
     * 执行状态, PENDING/RUNNING/SUCCESS/FAILED/CANCELLED.
     */
    @Schema(name = "status", description = "执行状态")
    private String status;

    /**
     * 错误信息.
     */
    @Schema(name = "errorMsg", description = "错误信息")
    private String errorMsg;

    /**
     * 单条语句耗时, 毫秒.
     */
    @Schema(name = "costMs", description = "单条语句耗时(毫秒)")
    private Long costMs;

    /**
     * 本段SQL的执行结果, 仅在本段执行成功且有结果集时返回.
     */
    @Schema(name = "result", description = "本段SQL的执行结果")
    private ExecSqlResultDto result;
}

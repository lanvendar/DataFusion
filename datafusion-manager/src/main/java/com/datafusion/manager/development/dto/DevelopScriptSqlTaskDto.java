package com.datafusion.manager.development.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.UUID;

/**
 * SQL脚本任务响应DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Data
@Accessors(chain = true)
@Schema(name = "DevelopScriptSqlTaskDto", description = "SQL脚本任务响应DTO")
public class DevelopScriptSqlTaskDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 任务名称.
     */
    @Schema(name = "name", description = "任务名称")
    private String name;

    /**
     * 任务编码.
     */
    @Schema(name = "code", description = "任务编码")
    private String code;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * SQL脚本（与库表 script jsonb 一致，可为 JSON 字符串节点或对象）.
     */
    @Schema(name = "script", description = "SQL脚本(jsonb)，JSON 字符串或对象均可")
    private JsonNode script;

    /**
     * 变量(JSON).
     */
    @Schema(name = "variables", description = "变量(JSON)")
    private JsonNode variables;

    /**
     * 发布状态.
     */
    @Schema(name = "publishStatus", description = "发布状态")
    private Boolean publishStatus;

    /**
     * 删除标识：0正常 1已删除.
     */
    @Schema(name = "modelStatus", description = "删除标识：0正常 1已删除")
    private Integer modelStatus;

    /**
     * 发布时间.
     */
    @Schema(name = "publishTime", description = "发布时间")
    private Date publishTime;

    /**
     * 任务分组ID.
     */
    @Schema(name = "groupId", description = "任务分组ID")
    private UUID groupId;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    /**
     * 更新人.
     */
    @Schema(name = "updater", description = "更新人")
    private String updater;

    /**
     * 更新时间.
     */
    @Schema(name = "updateTime", description = "更新时间")
    private Date updateTime;
}

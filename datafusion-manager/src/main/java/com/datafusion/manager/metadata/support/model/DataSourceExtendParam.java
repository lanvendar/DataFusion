package com.datafusion.manager.metadata.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据源拓展参数.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/21
 * @since 3.7.2, 2024/11/21
 */
@Data
@Builder
public class DataSourceExtendParam {
    /**
     * 名称.
     */
    @Schema(description = "名称")
    private String name;

    /**
     * 标识符.
     */
    @Schema(description = "标识符")
    private String identifier;

    /**
     * 值.
     */
    @Schema(description = "值")
    private String value;

    /**
     * 默认值.
     */
    @Schema(description = "默认值")
    private String defaultValue;

    /**
     * 选项.
     */
    @Schema(description = "选项")
    private List<String> options;

}

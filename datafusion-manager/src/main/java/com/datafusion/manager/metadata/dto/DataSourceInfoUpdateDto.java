package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 数据源更新DTO.
 *
 * @author david
 * @version 3.6.4, 2024/8/15
 * @since 3.6.4, 2024/8/15
 */
@Data
@Schema(name = "DataSourceInfoUpdateDto", description = "数据源更新DTO")
public class DataSourceInfoUpdateDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    @NotNull(message = "id不能为空")
    private UUID id;

    /**
     * 账号.
     */
    @Schema(name = "username", description = "账号")
    private String username;

    /**
     * 密码.
     */
    @Schema(name = "password", description = "密码")
    private String password;

    /**
     * 是否ssl连接.
     */
    @Schema(name = "useSsl", description = "是否ssl连接", defaultValue = "true", hidden = true)
    private boolean useSsl;

    /**
     * 时区.
     */
    @Schema(name = "serverTimezone", description = "时区", defaultValue = "Asia/Shanghai", hidden = true)
    private String serverTimezone = "Asia/Shanghai";

    /**
     * 元数据配置-hiv、starrocks.
     */
    @Schema(name = "metadataInfo", description = "元数据,目前只有hive、starrocks有值")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode metadataInfo;

    /**
     * 拓展信息.
     */
    @Schema(description = "元数据,- 拓展信息，autoReconnect：是否自动连接、useSsl：是否ssl连接、serverTimezone：时区等")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode extendParam;
}

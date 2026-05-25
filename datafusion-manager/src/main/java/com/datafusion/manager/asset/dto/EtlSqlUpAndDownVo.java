package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/5/14
 * @since 2026/5/14
 */

@Data
public class EtlSqlUpAndDownVo {

    /**
     * oss bucketName.
     */
    @Schema(name = "bucketName", description = "oss bucketName")
    private String bucketName;

    /**
     * 上传oss时的本地路径.
     */
    @Schema(name = "uploadLocalPath", description = "上传oss时的本地路径")
    @NotNull(message = "上传本地路径不能为空")
    private String uploadLocalPath;

    /**
     * oss文件前缀路径.
     */
    @Schema(name = "ossfilePrefixPath", description = "oss文件前缀路径")
    @NotNull(message = "文件前缀路径不能为空")
    private String ossfilePrefixPath;

}

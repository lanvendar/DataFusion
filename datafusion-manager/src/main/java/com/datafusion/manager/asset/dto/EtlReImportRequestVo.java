package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/5
 * @since 2025/11/5
 */
@Data
public class EtlReImportRequestVo {
    
    /**
     * 根路径.
     */
    @Schema(name = "rootPath", description = "跟路径,调用不需要传参")
    private String rootPath;
    
    /**
     * 相对gitlab路径.
     */
    @Schema(name = "filePath", description = "相对gitlab路径")
    private String filePath;
    
    /**
     * 文件名称.
     */
    @Schema(name = "fileName", description = "文件名称")
    private String fileName;
}

package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 表结构对比实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/9
 * @since 2025/9/9
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetaDataCompareDto {

    /**
     * 数据源ID.
     */
    @Schema(name = "datasourceId", description = "数据库ID")
    @NotNull
    private UUID datasourceId;
    
    /**
     * 需要对比的表名.
     */
    @NotEmpty(message = "tableNames不能为空")
    @Schema(name = "tableNames", description = "需要对比的表信息")
    private List<String> tableNames;
    
    
    /**
     * 源表到目标表是否有前缀关系.
     */
    @Schema(name = "isAddPrefix", description = "表到目标表是否有前缀关系,null表示无前缀关系,true,表到目标表位增加,false表到目标是删除")
    private Boolean isAddPrefix;
    
    /**
     * 前缀.
     */
    @Schema(name = "prefix", description = "前缀")
    private String prefix;
    
    /**
     * 源表到目标表是否有后缀关系.
     */
    @Schema(name = "isAddSuffix", description = "表到目标表是否有后缀关系,null表示无后缀关系,true,表到目标表增加后缀,false表到目标是删除后缀")
    private Boolean isAddSuffix;
    
    /**
     * 后缀.
     */
    @Schema(name = "suffix", description = "后缀")
    private String suffix;
    
    
    
}

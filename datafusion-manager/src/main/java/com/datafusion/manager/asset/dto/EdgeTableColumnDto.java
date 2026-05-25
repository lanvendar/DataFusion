package com.datafusion.manager.asset.dto;

import com.datafusion.manager.metadata.dto.EdgeColumnInfoDto;
import lombok.Data;

import java.util.List;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/16
 * @since 2025/10/16
 */
@Data
public class EdgeTableColumnDto {
    
    /**
     * 目标字段表信息.
     */
    private EdgeColumnInfoDto targetColumnInfo;
    
    /**
     * 来源字段信息,一个目标字段可能有多个来源字段.
     */
    private List<EdgeColumnInfoDto> sourceColumnInfos;
}

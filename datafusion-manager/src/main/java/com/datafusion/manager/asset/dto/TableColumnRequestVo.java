package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
public class TableColumnRequestVo {
    
    /**
     * nodeType.
     */
    private String nodeType;
    
    /**
     * nodeName.
     */
    private String nodeName;
    
    /**
     * dataSourceName.
     */
    private String dataSourceName;
}

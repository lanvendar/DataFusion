package com.datafusion.manager.metadata.support.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * RunSqlParam.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/15
 * @since 2025/9/15
 */
@Data
@AllArgsConstructor
public class RunSqlParam {
    
    /**
     * 默认的set.
     */
    private String defaultSet;
    
    /**
     * 执行的sql.
     */
    private String runSql;
}

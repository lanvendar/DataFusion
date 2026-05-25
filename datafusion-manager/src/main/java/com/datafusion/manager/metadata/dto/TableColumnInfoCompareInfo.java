package com.datafusion.manager.metadata.dto;

import com.datafusion.manager.metadata.enums.TableColumnCompareEnum;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import lombok.Data;

/**
 * 表字段对比信息.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
@Data
public class TableColumnInfoCompareInfo extends TableColumnInfo {
    
    /**
     * 对比结果.
     */
    private TableColumnCompareEnum compareResult;
}

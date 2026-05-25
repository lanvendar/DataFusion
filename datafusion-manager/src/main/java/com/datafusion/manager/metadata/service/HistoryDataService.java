package com.datafusion.manager.metadata.service;

import java.util.UUID;

/**
 * 历史版本信息.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
public interface HistoryDataService {
    
    /**
     * 保留当前历史.
     *
     * @param tableId tableId
     * @return boolean
     */
    boolean saveSnapshot(UUID tableId);
    
}

package com.datafusion.manager.metadata.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 元数据导出service.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/6
 * @since 3.7.2, 2024/11/6
 */
public interface MetaDataExportService {

    /**
     * 导出表结构.
     *
     * @param tableIds 表ID
     * @param request  http请求
     * @param response http应答
     */
    void exportTableColumn(UUID tableIds, HttpServletRequest request, HttpServletResponse response);
}

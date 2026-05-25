package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.service.ColumnInfoHisService;
import com.datafusion.manager.metadata.service.ColumnInfoService;
import com.datafusion.manager.metadata.service.HistoryDataService;
import com.datafusion.manager.metadata.service.TableInfoHisService;
import com.datafusion.manager.metadata.service.TableInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;

/**
 * 历史版本信息帮助类.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryDataServiceImpl implements HistoryDataService {

    /**
     * 元数据-表服务.
     */
    @Autowired
    private TableInfoService tableInfoService;

    /**
     * 元数据-字段历史服务.
     */
    @Autowired
    private ColumnInfoService columnInfoService;

    /**
     * 元数据-表历史服务.
     */
    @Autowired
    private TableInfoHisService tableInfoHisService;

    /**
     * 元数据-字段历史服务.
     */
    @Autowired
    private ColumnInfoHisService columnInfoHisService;

    @Override
    public boolean saveSnapshot(UUID tableId) {
        //查询当前的表信息
        TableInfoEntity tableInfoDto = tableInfoService.getWithCheckNonNull(tableId);
        if (tableInfoDto == null) {
            return false;
        }
        //查询当前表的列信息
        List<ColumnInfoEntity> columnInfoList = columnInfoService.getByTableId(tableId);
        if (CollectionUtil.isEmpty(columnInfoList)) {
            return false;
        }
        String version = DateUtil.format(new Date(), PURE_DATE_PATTERN);
        List<TableInfoEntity> tableInfoEntityList = new ArrayList<>();
        tableInfoEntityList.add(tableInfoDto);
        if (!tableInfoHisService.customSaveOrUpdateBatch(tableInfoEntityList, version)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表历史版本信息保存失败");
        }
        if (!columnInfoHisService.customSaveOrUpdateBatch(columnInfoList, version)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表字段历史版本信息保存失败");
        }
        return true;
    }
}

package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.uuid.IdGenerator;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dao.TableInfoMapper;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.dto.TableInfoDto;
import com.datafusion.manager.metadata.dto.TableInfoQueryDto;
import com.datafusion.manager.metadata.dto.TableInfoSaveDto;
import com.datafusion.manager.metadata.dto.TableInfoUpdateDto;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.ColumnInfoHisIdEntity;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoHisIdEntity;
import com.datafusion.manager.metadata.service.ColumnInfoHisService;
import com.datafusion.manager.metadata.service.ColumnInfoService;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.HistoryDataService;
import com.datafusion.manager.metadata.service.TableInfoHisService;
import com.datafusion.manager.metadata.service.TableInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 元数据-表服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@Slf4j
@Service
public class TableInfoServiceImpl extends ServiceImpl<TableInfoMapper, TableInfoEntity> implements TableInfoService {
    
    
    /**
     * 元数据-数据源服务.
     */
    @Autowired
    private DataSourceInfoService dataSourceInfoService;
    
    /**
     * 元数据-字段信息服务.
     */
    @Autowired
    private TableInfoMapper tableInfoMapper;
    
    /**
     * 元数据-字段信息服务.
     */
    @Autowired
    private ColumnInfoService columnInfoService;
    
    /**
     * 元数据-字段信息服务.
     */
    @Autowired
    private ColumnInfoHisService columnInfoHisService;
    
    /**
     * 历史版本信息服务.
     */
    @Autowired
    private HistoryDataService historyDataService;
    
    /**
     * 元数据-表信息服务.
     */
    @Autowired
    private TableInfoHisService tableInfoHisService;
    
    /**
     * 获取并检查表非空.
     *
     * @param id 数据表ID
     * @return 数据表实体
     */
    @Override
    public TableInfoEntity getWithCheckNonNull(UUID id) {
        TableInfoEntity table = super.getById(id);
        if (null == table) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未知数据表");
        }
        
        return table;
    }
    
    /**
     * 分页查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    @Override
    public Result<PageResponse<TableInfoDto>> pageTableInfos(PageQuery<TableInfoQueryDto> query) {
        int totalCount = baseMapper.getPageTotalCount(query);
        if (totalCount == 0 || totalCount <= query.getOffset()) {
            return Result.success(PageResponse.emptyPage(query));
        }
        return Result.success(
                new PageResponse<TableInfoDto>(baseMapper.getPageTableList(query),
                        query.getSize(), query.getCurrent(), totalCount
                ));
        
    }
    
    
    /**
     * 列表查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    @Override
    public List<TableInfoDto> listTableInfos(TableInfoQueryDto query) {
        query = Optional.ofNullable(query).orElseGet(TableInfoQueryDto::new);
        return baseMapper.getTableList(query);
    }
    
    @Override
    public List<DataSourceTableColumnDto> getDataSourceTableColumns(UUID datasourceId, List<String> tables) {
        return tableInfoMapper.getDataSourceTableColumns(datasourceId, tables);
    }
    
    /**
     * 添加表信息.
     *
     * @param saveDto 表参数
     * @return 添加结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addTableInfo(TableInfoSaveDto saveDto) {
        UUID datasourceId = saveDto.getDatasourceId();
        dataSourceInfoService.getWithCheckNonNull(datasourceId);
        
        // 处理表信息
        UUID tableId = IdGenerator.createTableId(datasourceId, saveDto.getTableName());
        TableInfoEntity table = super.getById(tableId);
        if (Objects.nonNull(table)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表已存在");
        }
        
        table = new TableInfoEntity();
        BeanUtils.copyProperties(saveDto, table);
        table.setId(tableId);
        table.setIsView(false);
        table.setIsModify(true);
        table.setIsEqual(false); // 手动添加表默认结构不一致
        table.setCreator(HttpUtils.getCurrentUserName());
        table.setUpdater(HttpUtils.getCurrentUserName());
        table.setCreateTime(new Date());
        table.setUpdateTime(new Date());
        if (!super.save(table)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "保存失败");
        }
        
        return tableId;
    }
    
    /**
     * 更新表数据.
     *
     * @param updateDto 更新表数据
     * @return 更新结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTableInfo(TableInfoUpdateDto updateDto) {
        TableInfoEntity entity = getWithCheckNonNull(updateDto.getId());
        TableInfoEntity table = new TableInfoEntity();
        BeanUtils.copyProperties(entity, table);
        BeanUtils.copyProperties(updateDto, table);
        // TODO 更新人信息
        table.setUpdater(HttpUtils.getCurrentUserName());
        table.setUpdateTime(new Date());
        // 保留历史版本信息，版本按天存储
        historyDataService.saveSnapshot(table.getId());
        return super.updateById(table);
    }
    
    /**
     * 根据ID查询表信息.
     *
     * @param id 表ID
     * @return 表信息
     */
    @Override
    public TableInfoDto getTableInfo(UUID id) {
        TableInfoDto table = baseMapper.getTableInfo(id);
        if (Objects.isNull(table)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未查到表");
        }
        if (table.getIsView()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "视图暂不支持展示详情");
        }
        if (StringUtils.isEmpty(table.getTableType())) {
            table.setTableType("数据表");
        }
        
        return table;
    }
    
    /**
     * 根据ID删除表.
     *
     * @param id 表ID
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTableInfo(UUID id) {
        TableInfoEntity table = getWithCheckNonNull(id);
        
        // 删除校验-判断是否有被引用
        checkDeleteByTableId(table.getDatasourceId(), id);
        
        // 确保事务一致性
        List<ColumnInfoEntity> columnInfos = columnInfoService.getByTableId(id);
        if (CollectionUtil.isNotEmpty(columnInfos)) {
            if (!columnInfoService.deleteByTableId(id)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "字段历史版本信息删除失败");
            }
        }
        List<ColumnInfoHisIdEntity> columnInfoHisEntityList = columnInfoHisService.getByTableId(id);
        if (CollectionUtil.isNotEmpty(columnInfoHisEntityList)) {
            if (!columnInfoService.deleteByTableId(id)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "字段历史版本信息删除失败");
            }
        }
        if (!this.removeById(id)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表信息删除失败");
        }
        List<TableInfoHisIdEntity> tableInfoHisEntityList = tableInfoHisService.getByTableId(id);
        if (CollectionUtil.isNotEmpty(tableInfoHisEntityList)) {
            if (!tableInfoHisService.deleteByTableId(id)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表历史版本信息删除失败");
            }
        }
        return true;
    }
    
    /**
     * 查询表.
     *
     * @param datasourceId 数据源ID
     * @return 表实体信息
     */
    @Override
    public List<TableInfoEntity> getTables(UUID datasourceId) {
        return getTables(datasourceId, Collections.emptyList());
    }
    
    /**
     * 查询表.
     *
     * @param datasourceId 数据源ID
     * @param tables       表名称集合
     * @return 表实体信息
     */
    @Override
    public List<TableInfoEntity> getTables(UUID datasourceId, List<String> tables) {
        Wrapper<TableInfoEntity> query = Wrappers.<TableInfoEntity>lambdaQuery() //
                .eq(TableInfoEntity::getDatasourceId, datasourceId) //
                .in(CollectionUtils.isNotEmpty(tables), TableInfoEntity::getTableName, tables);
        
        return super.list(query);
    }
    
    /**
     * 根据数据源ID查询表数量.
     *
     * @param datasourceId 数据源ID
     * @return 数据源表数量
     */
    @Override
    public Long getTableInfoCount(UUID datasourceId) {
        return baseMapper.getTableCount(datasourceId);
    }

    /**
     * 根据表ID名称检查是否能删除相关的数据或记录.
     *
     * @param dsId    数据库ID
     * @param tableId 表ID
     */
    private void checkDeleteByTableId(UUID dsId, UUID tableId) {
        /*        if (CollectionUtils.isEmpty(metaDataRefQueryService)) {
            return;
        }
        
        String objects = metaDataRefQueryService.stream()
                .map(queryService -> queryService.getObjectByTableId(dsId, tableId))
                .filter(StringUtils::isNotEmpty).collect(Collectors.joining(";"));
        if (StringUtils.isNotEmpty(objects)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,String.format("数据库表被%s引用，不能删除", objects));
        }*/
    }

    /**
     * 根据数据源id删除该ID下的所有表.
     *
     * @param query 数据源ID
     * @return 操作结果
     */
    @Override
    public Boolean deleteTablesByDataSourceId(RetrieveMetaDataDto query) {
        return baseMapper.deleteTablesByDataSourceId(query);
    }
}

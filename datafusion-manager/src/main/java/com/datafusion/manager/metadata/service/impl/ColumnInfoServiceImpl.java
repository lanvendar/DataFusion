package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.common.type.TypeInfoParser;
import com.datafusion.common.uuid.IdGenerator;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.metadata.dao.ColumnInfoMapper;
import com.datafusion.manager.metadata.dto.ColumnInfoDto;
import com.datafusion.manager.metadata.dto.ColumnInfoQueryDto;
import com.datafusion.manager.metadata.dto.ColumnInfoSaveDto;
import com.datafusion.manager.metadata.dto.ColumnInfoUpdateDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.service.ColumnInfoHisService;
import com.datafusion.manager.metadata.service.ColumnInfoService;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.HistoryDataService;
import com.datafusion.manager.metadata.service.TableInfoHisService;
import com.datafusion.manager.metadata.service.TableInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

/**
 * 元数据-字段信息服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnInfoServiceImpl extends ServiceImpl<ColumnInfoMapper, ColumnInfoEntity> implements ColumnInfoService {
    
    /**
     * 元数据-数据库服务.
     */
    @Autowired
    private DataSourceInfoService dataSourceInfoService;
    
    /**
     * 元数据-表服务.
     */
    @Autowired
    private TableInfoService tableInfoService;
    
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
    
    /**
     * 历史数据服务.
     */
    @Autowired
    private HistoryDataService historyDataService;
    
    
    /**
     * 获取并检查字段非空.
     *
     * @param id 字段ID
     * @return 字段实体
     */
    @Override
    public ColumnInfoEntity getWithCheckNonNull(UUID id) {
        ColumnInfoEntity column = super.getById(id);
        if (null == column) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未知字段信息");
        }
        
        return column;
    }
    
    /**
     * 分页查询.
     *
     * @param queryDto 查询条件
     * @return 字段信息
     */
    @Override
    public Result<PageResponse<ColumnInfoDto>> pageColumnInfos(PageQuery<ColumnInfoQueryDto> queryDto) {
        IPage<ColumnInfoEntity> page = new Page<>(queryDto.getCurrent(), queryDto.getSize());
        Wrapper queryWrapper = buildQueryWrapper(queryDto.getOption());
        page = baseMapper.selectPage(page, queryWrapper);
        PageResponse<ColumnInfoDto> pageResponse = new PageResponse<>();
        
        // 注意：IPage 的字段是 long 类型，PageResponse 是 Integer，这里进行类型转换
        pageResponse.setTotal((int) page.getTotal());
        pageResponse.setCurrent((int) page.getCurrent());
        pageResponse.setSize((int) page.getSize());
        
        // 4. 映射数据记录列表，并处理 null 的情况
        List<ColumnInfoEntity> records = page.getRecords();
        pageResponse.setDataList(CollectionUtil.isNotEmpty(records) ? records.stream().map(dto -> {
            ColumnInfoDto vo = new ColumnInfoDto();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        }).collect(Collectors.toList()) : Collections.emptyList());
        return Result.success(pageResponse);
    }
    
    /**
     * 根据查询条件构建 MyBatis-Plus 的 QueryWrapper.
     *
     * @param query 查询参数 DTO 对象
     * @return 构建好的 QueryWrapper
     */
    private QueryWrapper<ColumnInfoEntity> buildQueryWrapper(ColumnInfoQueryDto query) {
        // 对应 <mapper> 中的 select t.*, 我们在 Wrapper 中主要关注 where 和 order by
        // 创建 QueryWrapper 实例，并指定泛型为对应的实体类，以获得更好的类型安全和提示
        QueryWrapper<ColumnInfoEntity> queryWrapper = new QueryWrapper<>();
        
        // 对应 XML 中的 <if test="query != null">
        if (query != null) {
            
            // 对应 <if test="query.tableId != null"> and t.table_id = #{query.tableId}
            // 使用 eq 方法的第三个参数 (condition)，当 condition 为 true 时，该条件才会生效
            queryWrapper.eq(query.getTableId() != null, "table_id", query.getTableId());
            
            // 对应 <if test="query.projectId != null"> and t.project_id = #{query.projectId}
            //queryWrapper.eq(query.getProjectId() != null, "project_id", query.getProjectId());
            
            // 对应 <if test="query.columnName != null and query.columnName != ''">
            // and (t.column_name like ... or t.column_desc like ...)
            // 使用 StrUtil.isNotBlank 判断非 null 且非空字符串
            // 使用 Lambda 表达式构建 ( A or B ) 结构
            queryWrapper.and(
                    StrUtil.isNotBlank(query.getColumnName()), // 这个 and 块的生效条件
                    wq -> wq.like("column_name", query.getColumnName())
                            .or()
                            .like("column_desc", query.getColumnName())
            );
        }
        
        // 对应 order by t.column_serial asc
        // 这个排序条件在 <where> 之外，所以无论 query 是否为 null 都应该应用
        queryWrapper.orderByAsc("column_serial");
        
        return queryWrapper;
    }
    
    /**
     * 列表查询.
     *
     * @param queryDto 查询条件
     * @return 字段信息
     */
    @Override
    public List<ColumnInfoDto> listColumnInfos(ColumnInfoQueryDto queryDto) {
        queryDto = Optional.ofNullable(queryDto).orElseGet(ColumnInfoQueryDto::new);
        
        return baseMapper.getColumnList(queryDto);
    }
    
    /**
     * 添加字段.
     *
     * @param saveDto 参数
     * @return 字段ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addColumnInfo(ColumnInfoSaveDto saveDto) {
        UUID tableId = saveDto.getTableId();
        TableInfoEntity table = tableInfoService.getWithCheckNonNull(tableId);
        
        UUID columnId = IdGenerator.createColumnId(table.getId(), saveDto.getColumnName());
        ColumnInfoEntity column = super.getById(columnId);
        if (Objects.nonNull(column)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表字段已存在");
        }
        
        column = new ColumnInfoEntity();
        BeanUtils.copyProperties(saveDto, column);
        //暂时先注销掉
        //column.setId(columnId);
        column.setTableName(table.getTableName());
        
        DataSourceInfoEntity datasource = dataSourceInfoService.getWithCheckNonNull(table.getDatasourceId());
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(datasource.getDatabaseType()));
        TypeInfo typeInfo = parser.parse(column.getColumnType());
        column.setJavaType(typeInfo.getSimpleJavaType());
        
        // 新增字段的时候，前端没传查询类型，就默认初始化成java类型
        column.setViewType(StringUtils.isBlank(column.getViewType()) ? typeInfo.getSimpleJavaType() : column.getViewType());
        column.setId(columnId);
        // TODO 创建人信息
        column.setUpdater(HttpUtils.getCurrentUserName());
        column.setCreator(HttpUtils.getCurrentUserName());
        column.setUpdateTime(new Date());
        column.setCreateTime(new Date());
        //保留历史版本信息，版本按天存储
        historyDataService.saveSnapshot(table.getId());
        
        if (!super.save(column)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "保存失败");
        }
        
        return columnId;
    }
    
    /**
     * 更新字段信息.
     *
     * @param updateDto 更新参数
     * @return 字段ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateColumnInfo(ColumnInfoUpdateDto updateDto) {
        ColumnInfoEntity column = getWithCheckNonNull(updateDto.getId());
        BeanUtils.copyProperties(updateDto, column);
        String viewType = column.getViewType();
        if (StringUtils.isBlank(column.getViewType())) {
            column.setViewType(viewType);
        }
        // TODO 更新人信息
        column.setUpdateTime(new Date());
        column.setUpdater(HttpUtils.getCurrentUserName());
        //保留历史版本信息，版本按天存储
        historyDataService.saveSnapshot(column.getTableId());
        return super.updateById(column);
    }
    
    /**
     * 查询字段.
     *
     * @param id 字段ID
     * @return 字段信息
     */
    @Override
    public ColumnInfoDto getColumnInfo(UUID id) {
        ColumnInfoEntity column = getWithCheckNonNull(id);
        ColumnInfoDto dto = new ColumnInfoDto();
        BeanUtils.copyProperties(column, dto);
        
        return dto;
    }
    
    /**
     * 删除字段.
     *
     * @param id 字段ID
     * @return 删除结果·
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteColumnInfo(UUID id) {
        ColumnInfoEntity column = getWithCheckNonNull(id);
        //保留历史版本信息，版本按天存储
        historyDataService.saveSnapshot(column.getTableId());
        return super.removeById(column);
    }
    
    /**
     * 根据表ID删除字段.
     *
     * @param tableId 表ID
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByTableId(UUID tableId) {
        Wrapper<ColumnInfoEntity> query = Wrappers.<ColumnInfoEntity>lambdaQuery().eq(ColumnInfoEntity::getTableId, tableId);
        return super.remove(query);
    }
    
    /**
     * 根据表ID查询字段.
     *
     * @param tableId 表ID
     * @return 字段列表
     */
    @Override
    public List<ColumnInfoEntity> getByTableId(UUID tableId) {
        return getByTableIds(Collections.singletonList(tableId));
    }
    
    /**
     * 根据表ID集合查询字段.
     *
     * @param tableIds 表ID集合
     * @return 字段列表
     */
    @Override
    public List<ColumnInfoEntity> getByTableIds(List<UUID> tableIds) {
        if (CollectionUtils.isEmpty(tableIds)) {
            return Collections.emptyList();
        }
        
        Wrapper<ColumnInfoEntity> query = Wrappers.<ColumnInfoEntity>lambdaQuery().in(ColumnInfoEntity::getTableId, tableIds)
                .orderByAsc(ColumnInfoEntity::getColumnSerial);
        return super.list(query);
    }

    /**
     * 根据数据源id删除该ID下的所有字段.
     *
     * @param query 数据源ID
     * @return 操作结果
     */

    @Override
    public Boolean deleteColumnsByDataSourceId(RetrieveMetaDataDto query){
        return baseMapper.deleteColumnsByDataSourceId(query);
    }




}

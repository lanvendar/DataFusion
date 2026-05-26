package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.AssertUtils;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.uuid.IdGenerator;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.datasource.ConnectorFactory;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.dao.DataSourceInfoMapper;
import com.datafusion.manager.metadata.dto.DataSourceInfoCopyDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoSaveDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoUpdateDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.TableInfoService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.datafusion.manager.utils.AesUtil;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 元数据-数据库服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceInfoServiceImpl extends ServiceImpl<DataSourceInfoMapper, DataSourceInfoEntity>
        implements DataSourceInfoService {

    /**
     * 元数据-表服务.
     */
    @Autowired
    private TableInfoService tableInfoService;

    /**
     * 动态数据源创建工厂.
     */
    private final ConnectorFactory dataSourceFactory;

    /**
     * 检查并获取数据源.
     *
     * @param id 数据源ID
     * @return 数据源信息
     */
    @Override
    public DataSourceInfoEntity getWithCheckNonNull(UUID id) {
        DataSourceInfoEntity entity = super.getById(id);
        if (null == entity) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未知数据源");
        }

        return entity;
    }

    /**
     * 分页查询数据库信息.
     *
     * @param query 查询条件
     * @return 数据库信息
     */
    @Override
    public PageResponse<DataSourceInfoDto> pageDataSource(PageQuery<DataSourceInfoQueryDto> query) {
        int totalCount = baseMapper.pageCount(query);
        if (totalCount == 0 || totalCount <= query.getOffset()) {
            return PageResponse.emptyPage(query);
        }
        return new PageResponse<>(baseMapper.pageList(query), query.getSize(), query.getCurrent(), totalCount);
    }

    /**
     * 查询数据库.
     *
     * @param query 查询参数
     * @return 查询结果
     */
    @Override
    public List<DataSourceInfoDto> listDataSource(DataSourceInfoQueryDto query) {
        query = Optional.ofNullable(query).orElseGet(DataSourceInfoQueryDto::new);
        return baseMapper.list(query);
    }

    /**
     * 根据数据表ID集合查询数据源.
     *
     * @param tableIds 数据表ID集合
     * @return 数据源列表
     */
    @Override
    public List<DataSourceInfoEntity> listByTableIds(List<UUID> tableIds) {
        if (CollectionUtils.isNotEmpty(tableIds)) {
            return baseMapper.listByTableIds(tableIds);
        }

        return Collections.emptyList();
    }

    @Override
    public UUID copyDataSource(DataSourceInfoCopyDto copyDto) {
        DataSourceInfoEntity entity = getById(copyDto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "根据id并没有查询到数据源");
        }
        if (entity.getName().equals(copyDto.getName())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "数据源名称和复制数据源名称相同");
        }
        if (entity.getDatabaseName().equals(copyDto.getDatabaseName())
                && entity.getSchemaName().equals(copyDto.getSchemaName())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "复制的数据源db和schema不能和以前相同");
        }
        DataSourceInfoSaveDto saveDto = new DataSourceInfoSaveDto();
        BeanUtils.copyProperties(entity, saveDto);
        BeanUtils.copyProperties(copyDto, saveDto);
        //判断密码有没有修改？
        if (StrUtil.isNotEmpty(saveDto.getPassword())) {
            if (!AesUtil.isSecret(saveDto.getPassword())) {
                saveDto.setPassword(AesUtil.encrypt(saveDto.getPassword()));
            }
        }
        return this.addDataSource(saveDto);
    }

    /**
     * 添加数据源.
     *
     * @param saveDto 数据源参数
     * @return 添加结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addDataSource(DataSourceInfoSaveDto saveDto) {
        DataSourceInfoEntity entity = new DataSourceInfoEntity();
        BeanUtils.copyProperties(saveDto, entity);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(entity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(entity);
        DataSourceInfo ds = databaseTransformService.transformDataSourceInfo(entity);
        if (!databaseService.tryConnect(ds)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源连接失败，请确认数据库或模式是否正确");
        }
        // 校验数据源唯一性
        checkUniqueByJdbcUrl(null, ds);

        BeanUtils.copyProperties(ds, entity);
        UUID id = IdGenerator.createDsId(saveDto.getName());
        DataSourceInfoEntity dataSourceInfoEntity = this.getById(id);
        if (dataSourceInfoEntity != null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源名称已经存在");
        }
        entity.setId(id);
        //数据源名称要唯一
        entity.setTableCount(ds.getTableCount());
        //todo 用户ID获取方式
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        entity.setCreateTime(new Date());
        if (StrUtil.isNotEmpty(entity.getPassword())) {
            if (!AesUtil.isSecret(entity.getPassword())) {
                entity.setPassword(AesUtil.encrypt(entity.getPassword()));
            }
        }
        boolean saved = save(entity);
        if (!saved) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据库保存失败");
        }
        return entity.getId();
    }

    /**
     * 修改数据源.
     *
     * @param updateDto 数据源参数
     * @return 修改结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDataSource(DataSourceInfoUpdateDto updateDto) {
        DataSourceInfoEntity entity = getById(updateDto.getId());
        AssertUtils.notNull(entity, "数据源不存在");
        //处理密码问题,密码如果不为默认值,则修改
        updateDto.setPassword(updateDto.getPassword());
        /*if (updateDto.getPassword().equals(DesensitizedUtil.password(entity.getPassword()))
                || StrUtil.isEmpty(updateDto.getPassword())) {
            updateDto.setPassword(entity.getPassword());
        }*/
        // 校验数据源连接名称唯一性
        BeanUtils.copyProperties(updateDto, entity);
        entity.setUpdateTime(new Date());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(entity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(entity);
        DataSourceInfo ds = databaseTransformService.transformDataSourceInfo(entity);
        DataSourceInfo testConnectDs = new DataSourceInfo();
        BeanUtils.copyProperties(ds, testConnectDs);
        if (!databaseService.tryConnect(testConnectDs)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_SQL_EXCUTE_ERROR_C0313, "测试连接失败,请检查数据库配置");
        }
        entity.setJdbcUrl(ds.getJdbcUrl());
        // 校验数据源唯一性
        checkUniqueByJdbcUrl(updateDto.getId(), ds);

        // 更新元数据和主数据源
        refreshCacheIfChanged(entity, databaseTransformService, ds);

        //判断密码有没有修改？
        if (StrUtil.isNotEmpty(entity.getPassword())) {
            if (!AesUtil.isSecret(entity.getPassword())) {
                entity.setPassword(AesUtil.encrypt(entity.getPassword()));
            }
        }
        // 设置数据库表总数
        return updateById(entity);
    }

    /**
     * 根据ID查询数据源.
     *
     * @param id 数据源ID
     * @return 数据源信息
     */
    @Override
    public DataSourceInfoDto getDataSource(UUID id) {
        DataSourceInfoEntity entity = getWithCheckNonNull(id);
        DataSourceInfoDto dto = new DataSourceInfoDto();
        BeanUtils.copyProperties(entity, dto);

        return dto;
    }

    @Override
    public DataSourceInfoDto getDataSourceByTableId(UUID tableId) {
        DataSourceInfoDto ds = baseMapper.getDataSourceByTableId(tableId);
        if (null == ds) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "未找到数据源");
        }
        return ds;
    }

    /**
     * 根据ID删除数据源.
     *
     * @param id 数据源ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDataSource(UUID id) {
        Long tableCount = tableInfoService.getTableInfoCount(id);
        if (Objects.nonNull(tableCount) && tableCount > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源下存在数据表");
        }

        //todo 暂时先注释掉,主要是检查数据元是否被同步所引用
        //checkDeleteByDsId(entity.getId());
        //关闭数据源
        dataSourceFactory.closeDataSources(id.toString());
        return super.removeById(id);
    }

    /**
     * 测试数据源连接.
     *
     * @param dto 数据源信息
     * @return 测试连接结果
     */
    @Override
    public boolean testConnect(DataSourceInfoSaveDto dto) {
        DataSourceInfoEntity dataSource = new DataSourceInfoEntity();
        BeanUtils.copyProperties(dto, dataSource);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dataSource);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dataSource);
        return databaseService.tryConnect(databaseTransformService.transformDataSourceInfo(dataSource));
    }

    /**
     * 如果数据源信息有变化，则进行刷新.
     *
     * @param entity                   数据源信息实体，包含数据源的详细信息
     * @param databaseTransformService 数据库服务接口，用于执行数据库操作
     * @param ds                       当前的数据源信息，用于比较是否有变化
     */
    private void refreshCacheIfChanged(DataSourceInfoEntity entity, TransformSupport databaseTransformService,
                                       DataSourceInfo ds) {
        // 检查并刷新元数据
        if (entity.getMetadataInfo() != null && !entity.getMetadataInfo().isNull()) {
            DataSourceInfoEntity meta = JacksonUtils.tryObj2Bean(entity.getMetadataInfo(), DataSourceInfoEntity.class);
            DataSourceInfo metaDs = databaseTransformService.transformDataSourceInfo(meta);

            // starrocks metadataInfo 只有jdbcurl，没有其他信息
            if (StringUtils.isNotEmpty(metaDs.getHost()) && dataSourceFactory.checkIsChanged(metaDs)) {
                try {
                    dataSourceFactory.refreshDataSources(metaDs);
                } catch (Exception e) {
                    log.error("刷新数据源缓存失败", e);
                }
            }
        }

        // 检查并刷新主数据源
        if (dataSourceFactory.checkIsChanged(ds)) {
            try {
                dataSourceFactory.refreshDataSources(ds);
            } catch (Exception e) {
                log.error("刷新数据源缓存失败", e);
            }
        }
    }

    /**
     * 检查数据源连接名称是否唯一.
     *
     * @param id   数据源ID
     * @param name 数据源连接名称
     */
    private void checkUniqueByName(@NotNull(message = "id不能为空") UUID id,
                                   @NotEmpty(message = "name不能为空") String name) {
        // 查询是否存在同名数据源（排除自身）
        //todo 暂时先注释掉
        /*LambdaQueryWrapper<DataSourceInfoEntity> queryWrapper = new LambdaQueryWrapper<>(DataSourceInfoEntity.class)
                .eq(DataSourceInfoEntity::getProjectId, SecurityUserUtils.getProjectId()).eq(
                DataSourceInfoEntity::getName, name).ne(id != null, DataSourceInfoEntity::getId, id);
        boolean exists = baseMapper.exists(queryWrapper);
        if (exists) {
            throw new DataSourceException("数据源连接名称已存在");
        }*/
    }

    /**
     * 验证数据源是否已存在.
     *
     * @param dsId dsId
     * @param ds   ds
     */
    private void checkUniqueByJdbcUrl(UUID dsId, DataSourceInfo ds) {
        if (ds == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "请输入数据源信息");
        }
        String jdbcUrl = ds.getJdbcUrl();
        if (StrUtil.isEmpty(jdbcUrl)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "jdbcUrl参数不能为空");
        }
        if (StrUtil.isEmpty(ds.getDatabaseName())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "dataBaseName参数不为空");
        }
        if (StrUtil.isEmpty(ds.getSchemaName())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_VERIFY_ERROR_C0504, "schemaName参数不能为空");
        }
        jdbcUrl = jdbcUrl.contains("?") ? jdbcUrl.substring(0, jdbcUrl.lastIndexOf("?")) : jdbcUrl;
        // 查询是否存在其他记录具有相同的 jdbcUrl 但不同的 id
        //todo 暂时注释掉
        int count = 0;
        count = baseMapper.countByJdbcUrl(dsId, jdbcUrl, ds.getDatabaseName(), ds.getSchemaName());
        if (count > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "已经有相同的数据库名和schema名存在");
        }
    }
}

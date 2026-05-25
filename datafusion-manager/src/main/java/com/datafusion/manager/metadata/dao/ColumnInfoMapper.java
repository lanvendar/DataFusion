package com.datafusion.manager.metadata.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.datafusion.manager.metadata.dto.ColumnInfoDto;
import com.datafusion.manager.metadata.dto.ColumnInfoQueryDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 元数据-表字段Mapper.
 *
 * @author david
 * @version 3.6.4, 2024/8/22
 * @since 3.6.4, 2024/8/22
 */
@Mapper
public interface ColumnInfoMapper extends BaseMapper<ColumnInfoEntity> {

    /**
     * 分页查询.
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 表字段信息
     */
    IPage<ColumnInfoDto> getColumnList(IPage<ColumnInfoDto> page, @Param("query") ColumnInfoQueryDto query);

    /**
     * 查询列表.
     *
     * @param query 查询条件
     * @return 表字段信息
     */
    List<ColumnInfoDto> getColumnList(@Param("query") ColumnInfoQueryDto query);

    /**
     * 根据数据源id删除该ID下的所有字段.
     *
     * @param query 数据源ID
     * @return 操作结果
     */
    Boolean deleteColumnsByDataSourceId(@Param("query") RetrieveMetaDataDto query);

}

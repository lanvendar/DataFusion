package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.po.AssetEtlProcessEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * ETL 过程记录 Mapper.
 *
 * @author xufeng
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Mapper
public interface AssetEtlProcessMapper extends BaseMapper<AssetEtlProcessEntity> {

}

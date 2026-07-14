package com.datafusion.manager.metadata.support;

import com.aliyun.odps.data.Record;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.dto.ColumnViewConfigDto;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareResultDto;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.DataSourceExtendParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.MetaDataQuery;
import com.datafusion.manager.metadata.support.model.RunSqlParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 元数据支持接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public interface MetaDataSupport {
    /**
     * 支持的数据库类型.
     *
     * @return 受支持类型
     */
    DatabaseTypeEnum support();
    
    /**
     * 获取数据源默认扩展参数.
     * 根据数据库类型获取与其关联的扩展参数列表这些扩展参数用于描述数据源的额外配置信息，
     * 例如连接池属性、加密密钥等这些参数对于数据源的配置和使用至关重要，能够提供更灵活的配置选项.
     *
     * @return 返回一个包含数据源扩展参数的列表如果数据源没有扩展参数，则返回空列表
     */
    List<DataSourceExtendParam> getDefaultExtendParams();

    /**
     * 获取数据源默认支持的数据类型.
     * @return 返回一个包含数据源支持的数据类型.
     */
    Set<String> getColumnDataTypes();
    
    /**
     * 根据数据源实体信息测试数据源连接.
     *
     * @param info 数据源实体
     * @return 测试连接结果
     */
    boolean tryConnect(DataSourceInfo info);
    
    /**
     * 根据数据源实体信息,获取所有数据库的表和字段信息.
     *
     * @param info 数据源实体
     * @return 数据库表字段信息
     */
    MetaDataInfo getMetaData(DataSourceInfo info);
    
    /**
     * 根据数据源实体及表名称信息，获取对应数据库的表和字段信息.
     *
     * @param info           数据源实体
     * @param queryCondition 查询条件
     * @return 指定数据库表字段信息
     */
    MetaDataInfo getMetaData(DataSourceInfo info, MetaDataQuery queryCondition);
    
    /**
     * 统计表行数和存储大小.
     *
     * @param ds        数据源信息
     * @param tableName 数据库表名
     * @return 表总数量
     */
    default long getTableCount(DataSourceInfo ds, String tableName) {
        return 0L;
    }
    
    /**
     * 统计表行数和存储大小.
     *
     * @param ds        数据源信息
     * @param tableName 数据库表名
     * @return 表总数量
     */
    default String getTableSize(DataSourceInfo ds, String tableName) {
        return "0kb";
    }
    
    /**
     * 根据分页信息获取数据表中的数据.
     *
     * @param ds        数据源信息,包含连接数据库所需的信息
     * @param condition 数据预览查询条件.
     * @return 返回一个列表，列表中的每个元素都是一个映射，
     * 映射的键是列名，值是对应列的值当前实现下，此列表为空
     */
    default List<Map<String, Object>> getDataPreview(DataSourceInfo ds, DataPreviewQuery condition) {
        return new ArrayList<>();
    }
    
    /**
     * 根据数据源类型获取页面配置信息.
     *
     * @param dataType dataType
     * @return 返回一个页面配置信息
     */
    ColumnViewConfigDto getDataTypeViewConfig(String dataType);
    
    /**
     * 获取表的更新语句.
     * @param info 数据源信息
     * @param tableColumnInfoCompareResultDto 表字段对比信息
     * @return 修改表DDL
     */
    String getAlterTableSql(DataSourceInfo info, TableColumnInfoCompareResultDto tableColumnInfoCompareResultDto);
    
    /**
     * 批量创建建表语句.
     *
     * @param ds       数据源信息
     * @param metaData 元数据信息
     * @return 返回一个页面配置信息
     */
    default String batchCreateTableDdl(DataSourceInfo ds, MetaDataInfo metaData) {
        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, ds.getDatabaseType() + " not support batch create ddl");
    }
    
    /**
     * 执行sql脚本.
     * @param ds 目标数据源
     * @param params 参数
     * @return 每段sql的执行结果
     */
    int [] runSql(DataSourceInfo ds, List<RunSqlParam> params);

    /**
     * 执行sql脚本.
     * @param ds 目标数据源
     * @param params 参数
     * @return 每段sql的执行结果
     */
    List<Record> execSql(DataSourceInfo ds, List<RunSqlParam> params);
    
}

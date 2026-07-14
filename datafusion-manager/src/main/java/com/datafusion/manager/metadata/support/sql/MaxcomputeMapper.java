package com.datafusion.manager.metadata.support.sql;

import com.datafusion.datasource.annotation.SqlDs;
import com.datafusion.datasource.annotation.SqlGet;
import com.datafusion.datasource.annotation.SqlParam;
import com.datafusion.datasource.annotation.SqlParams;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.spring.SqlMapper;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.MaxcomputeTableColumn;
import com.datafusion.manager.metadata.support.model.MaxcomputeTableCreateParam;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.SelectListColumn;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Maxcompute数据源Mapper.
 *
 * @author david
 * @version 3.6.4, 2024/9/4
 * @since 3.6.4, 2024/9/4
 */
@SqlGet(sqlKey = "maxcompute")
public interface MaxcomputeMapper extends SqlMapper<MaxcomputeTableColumn> {

    /**
     * 测试MySQL数据源连接.
     *
     * @param ds MySQL数据源
     * @return 测试连接结果
     */
    @SqlGet(sqlKey = "test_connect")
    Integer tryConnect(@SqlDs DataSourceInfo ds);

    /**
     * 获取maxcompute的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 与MySQL数据库名称一致
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<MaxcomputeTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo);

    /**
     * 获取maxcompute的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 与MySQL数据库名称一致
     * @param tableNames 表名称集合
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<MaxcomputeTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo,
                                       @SqlParam("tableNames") List<String> tableNames);

    /**
     * 获取表总数量.
     *
     * @param ds         数据源信息
     * @param schemaInfo schemaInfo
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countTables")
    Long countTables(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo);

    /**
     * 统计表行数.
     *
     * @param ds         数据源信息
     * @param tableName  数据库表名
     * @param schemaInfo schemaInfo
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countByTable")
    BigInteger countByTable(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo, @SqlParam("tableName") String tableName);


    /**
     * 统计表存储大小.
     *
     * @param ds        数据源信息
     * @param tableName 数据库表名
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countSizeByTable")
    long countSizeByTable(@SqlDs DataSourceInfo ds, @SqlParam("tableName") String tableName);

    /**
     * 分页查询表数据.
     *
     * @param ds         数据源信息
     * @param tableName  数据库表名
     * @param selectList 查询的列
     * @param whereSql   查询条件
     * @param orderSql   排序条件
     * @param offset     offset
     * @param pageSize   分页大小
     * @return 表总数量
     */
    @SqlGet(sqlKey = "pageList")
    List<Map> pageList(@SqlDs DataSourceInfo ds, @SqlParam("tableName") String tableName,
                       @SqlParam(value = "selectList") List<SelectListColumn> selectList,
                       @SqlParam("whereSql") String whereSql,
                       @SqlParam("orderSql") String orderSql,
                       @SqlParam("offset") Integer offset, @SqlParam("pageSize") Integer pageSize);


    /**
     * 创建maxcompute表.
     *
     * @param ds            Spark Thrift 数据源
     * @param params        建表参数
     * @return 建表结果
     */
    @SqlGet(sqlKey = "createTable", isBatch = true)
    int [] createTable(@SqlDs DataSourceInfo ds, @SqlParams List<MaxcomputeTableCreateParam> params);
    
    /**
     * 运行sql任务.
     *
     * @param ds            Spark Thrift 数据源
     * @param params        建表参数
     * @return 建表结果
     */
    @SqlGet(sqlKey = "runSql", isBatch = true)
    int [] runSql(@SqlDs DataSourceInfo ds, @SqlParams List<RunSqlParam> params);

    /**
     * 数据预览,返回多少条数.
     *
     * @param ds         数据源信息
     * @param condition  查询实体
     * @return 表总数量
     */
    @SqlGet(sqlKey = "getDataPreview")
    List<Map<String, Object>> getDataPreview(@SqlDs DataSourceInfo ds, @SqlParam("condition") DataPreviewQuery condition);

}

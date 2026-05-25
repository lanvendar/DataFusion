package com.datafusion.manager.metadata.support.sql;

import com.datafusion.datasource.annotation.SqlDs;
import com.datafusion.datasource.annotation.SqlGet;
import com.datafusion.datasource.annotation.SqlParam;
import com.datafusion.datasource.annotation.SqlParams;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.spring.SqlMapper;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.SelectListColumn;
import com.datafusion.manager.metadata.support.model.StarrocksTableColumn;
import com.datafusion.manager.metadata.support.model.StarrocksTableCreateParam;

import java.util.List;
import java.util.Map;

/**
 * StarrocksMapper.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/28
 * @since 3.7.2, 2024/11/28
 */

@SqlGet(sqlKey = "starrocks")
public interface StarrocksMapper extends SqlMapper<StarrocksTableColumn> {
    
    /**
     * 测试MySQL数据源连接.
     *
     * @param ds MySQL数据源
     * @return 测试连接结果
     */
    @SqlGet(sqlKey = "test_connect")
    Integer tryConnect(@SqlDs DataSourceInfo ds);
    
    /**
     * 创建表.
     *
     * @param ds     数据源
     * @param params 建表参数
     * @return 建表结果
     */
    @SqlGet(sqlKey = "createTable", isBatch = true)
    int[] createTable(@SqlDs DataSourceInfo ds, @SqlParams List<StarrocksTableCreateParam> params);
    
    /**
     * 获取MySQL的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 与MySQL数据库名称一致
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<StarrocksTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo);
    
    /**
     * 获取MySQL的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 与MySQL数据库名称一致
     * @param tableNames 表名称集合
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<StarrocksTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo,
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
     * 查询表信息.
     *
     * @param ds         数据源信息
     * @param tableName  数据库表名
     * @param schemaInfo schemaInfo
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countByTable")
    Long countByTable(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo, @SqlParam("tableName") String tableName);
    
    /**
     * 统计表存储大小.
     *
     * @param ds         数据源信息
     * @param tableName  数据库表名
     * @param schemaInfo schemaInfo
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countSizeByTable")
    Long countSizeByTable(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo, @SqlParam("tableName") String tableName);
    
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
     * 数据预览,返回多少条数.
     *
     * @param ds        数据源信息
     * @param condition 查询条件
     * @return 表总数量
     */
    @SqlGet(sqlKey = "getDataPreview")
    List<Map<String, Object>> getDataPreview(@SqlDs DataSourceInfo ds, @SqlParam("condition") DataPreviewQuery condition);
    
    
    /**
     * 根据分区count.
     *
     * @param ds           数据源信息
     * @param databaseName 数据库名
     * @param tableName    表名
     * @param dayPt        分区
     * @return be节点信息
     */
    @SqlGet(sqlKey = "countByDayPt")
    Long countByDayPt(@SqlDs DataSourceInfo ds,
                      @SqlParam("databaseName") String databaseName,
                      @SqlParam("tableName") String tableName,
                      @SqlParam("dayPt") String dayPt);
    
    /**
     * 查询所有的表.
     *
     * @param ds           数据源信息
     * @param databaseName 数据库名
     * @return 所有的表
     */
    @SqlGet(sqlKey = "showTables")
    List<String> showTables(@SqlDs DataSourceInfo ds,
                            @SqlParam("databaseName") String databaseName);
    
    /**
     * 运行sql任务.
     *
     * @param ds            Spark Thrift 数据源
     * @param params        建表参数
     * @return 建表结果
     */
    @SqlGet(sqlKey = "runSql", isBatch = true)
    int [] runSql(@SqlDs DataSourceInfo ds, @SqlParams List<RunSqlParam> params);
    
}

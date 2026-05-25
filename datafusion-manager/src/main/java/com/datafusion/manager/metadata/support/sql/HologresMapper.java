package com.datafusion.manager.metadata.support.sql;

import com.datafusion.datasource.annotation.SqlDs;
import com.datafusion.datasource.annotation.SqlGet;
import com.datafusion.datasource.annotation.SqlParam;
import com.datafusion.datasource.annotation.SqlParams;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.spring.SqlMapper;
import com.datafusion.datasource.spring.SqlRepository;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.HologresTableColumn;
import com.datafusion.manager.metadata.support.model.PostgresTableColumn;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.SelectListColumn;

import java.util.List;
import java.util.Map;

/**
 * PG数据库Mapper.
 *
 * @author david
 * @version 3.6.4, 2024/8/15
 * @since 3.6.4, 2024/8/15
 */
@SqlRepository
@SqlGet(sqlKey = "hologres")
public interface HologresMapper extends SqlMapper<PostgresTableColumn> {
    
    /**
     * 测试PG连接.
     *
     * @param ds PG数据源
     * @return 测试结果
     */
    @SqlGet(sqlKey = "test_connect")
    Integer tryConnect(@SqlDs DataSourceInfo ds);
    
    /**
     * 获取holo表信息.
     *
     * @param ds         PG数据源
     * @param schemaName schema名
     * @param tableName  表名
     * @return 测试结果
     */
    @SqlGet(sqlKey = "getTableRelkind")
    String getTableRelkind(@SqlDs DataSourceInfo ds, @SqlParam("schemaName") String schemaName, @SqlParam("tableName") String tableName);
    
    /**
     * 获取PG的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo PG为public
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<HologresTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo);
    
    /**
     * 获取PG的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo PG为public
     * @param tableNames 表名称集合
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<HologresTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo,
                                          @SqlParam("tableNames") List<String> tableNames);
    
    /**
     * 获取PG的表总数量.
     *
     * @param ds         数据源信息
     * @param schemaInfo PG为public
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
    long countByTable(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo, @SqlParam("tableName") String tableName);
    
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
     * @param pageNo     分页
     * @param pageSize   分页大小
     * @return 表总数量
     */
    @SqlGet(sqlKey = "pageList")
    List<Map<String, Object>> pageList(@SqlDs DataSourceInfo ds, @SqlParam("tableName") String tableName,
                                       @SqlParam(value = "selectList") List<SelectListColumn> selectList,
                                       @SqlParam("whereSql") String whereSql,
                                       @SqlParam("orderSql") String orderSql,
                                       @SqlParam("pageNo") Integer pageNo, @SqlParam("pageSize") Integer pageSize);
    
    /**
     * 数据预览,返回多少条数.
     *
     * @param ds         数据源信息
     * @param condition  查询实体
     * @return 表总数量
     */
    @SqlGet(sqlKey = "getDataPreview")
    List<Map<String, Object>> getDataPreview(@SqlDs DataSourceInfo ds, @SqlParam("condition") DataPreviewQuery condition);
    
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

/*
 * Copyright © 2000-2024 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

package com.datafusion.manager.metadata.support.sql;

import com.datafusion.datasource.annotation.SqlDs;
import com.datafusion.datasource.annotation.SqlGet;
import com.datafusion.datasource.annotation.SqlParam;
import com.datafusion.datasource.annotation.SqlParams;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.spring.SqlMapper;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.DmTableColumn;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.SelectListColumn;

import java.util.List;
import java.util.Map;


/**
 * Oracle数据源Mapper.
 *
 * @author david
 * @version 3.6.4, 2024/9/4
 * @since 3.6.4, 2024/9/4
 */

@SqlGet(sqlKey = "dm")
public interface DmMapper extends SqlMapper<DmTableColumn> {
    
    /**
     * 测试Oracle连接.
     *
     * @param ds 数据源信息
     * @return 查询字符串
     */
    @SqlGet(sqlKey = "try_connect")
    String tryConnect(@SqlDs DataSourceInfo ds);
    
    /**
     * 获取Oracle的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 一般是大写的账号名称
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<DmTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo);
    
    /**
     * 获取Oracle的表字段信息.
     *
     * @param ds         数据源信息
     * @param schemaInfo 一般是大写的账号名称
     * @param tableNames 表名称集合
     * @return 表结构信息
     */
    @SqlGet(sqlKey = "metadata")
    List<DmTableColumn> getMetaData(@SqlDs DataSourceInfo ds, @SqlParam("schemaInfo") String schemaInfo,
                                    @SqlParam("tableNames") List<String> tableNames);
    
    /**
     * 获取的表总数量.
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
     * @param ds        数据源信息
     * @param tableName 数据库表名
     * @return 表总数量
     */
    @SqlGet(sqlKey = "countByTable")
    long countByTable(@SqlDs DataSourceInfo ds, @SqlParam("tableName") String tableName);
    
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
                                       @SqlParam(value = "selectList") List<SelectListColumn> selectList, @SqlParam("whereSql") String whereSql,
                                       @SqlParam("orderSql") String orderSql, @SqlParam("pageNo") Integer pageNo,
                                       @SqlParam("pageSize") Integer pageSize);
    
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

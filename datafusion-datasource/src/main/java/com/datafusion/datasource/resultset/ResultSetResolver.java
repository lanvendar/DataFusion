package com.datafusion.datasource.resultset;

import java.lang.reflect.Type;

/**
 * sql查询结果集解析器,支持 行列结果集 对如下类型赋值.
 * 单行:
 * 1.基础类型 , 例如:{@code int, long, double, float, boolean, char, byte, short, String}等
 * 2.对象bean, 例如 {@code User, <T>}等
 * 3.Map集合, 例如: {@code Map<String, Object>}等
 * 多行:
 * 4.集合类型, 例如:{@code List<User>, List<T>, List<String>, Set<User>, Set<T>, Set<String>, List<Map<String, Object>>}等
 * 5.数组类型, 例如:{@code User[], T[], String[]}等
 *
 * @param <R> 查询行列结果集的泛型
 * @author lanvendar
 * @version 1.0.0, 2022/4/25
 * @since 2022/4/25
 */
public interface ResultSetResolver<R> {
    
    /**
     * 解析sql查询结果集.
     *
     * @param rs   sql结果集
     * @param type 返回对象类型
     * @return sql查询结果集
     */
    Object getResultSet(R rs, Type type);
}

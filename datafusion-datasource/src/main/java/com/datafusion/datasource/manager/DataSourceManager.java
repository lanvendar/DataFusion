package com.datafusion.datasource.manager;

/**
 * 数据源提供者.
 *
 * @param <T> 数据源类型
 * @param <C> 连接类型
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
public interface DataSourceManager<T, C> {
    
    /**
     * 获取数据源.
     *
     * @return 数据源
     */
    T getDataSource();
    
    /**
     * 从数据源获取一个连接.
     *
     * @return 连接
     */
    C getConnection();
    
    /**
     * 关闭整个数据源连接池.
     */
    void close();
}

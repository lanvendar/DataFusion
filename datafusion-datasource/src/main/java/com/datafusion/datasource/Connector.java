package com.datafusion.datasource;

import com.datafusion.datasource.model.ExecuteParam;

/**
 * 数据源和连接器接口,通常来说数据源和连接器是一对一配套的.
 * 它的实现类是长生命周期的,通常是单例,负责持有一个配置好的数据源.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
public interface Connector {
    /**
     * 执行数据源连接器中的数据源连接.
     *
     * @param executeParam 执行参数
     * @return 执行结果
     */
    Object execute(ExecuteParam executeParam);
    
    /**
     * 销毁此 Connector 实例及其持有的所有底层资源（如连接池）.
     * 此方法被调用后,该 Connector 实例将不可用.
     */
    void destroy();
}

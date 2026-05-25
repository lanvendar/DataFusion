package com.datafusion.manager.development.service.sql;

import com.datafusion.manager.metadata.po.DataSourceInfoEntity;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 开发侧SQL执行器抽象.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
public interface DevSqlExecutor {

    /**
     * 当前执行器是否支持给定数据源.
     *
     * @param dsEntity 数据源
     * @return 是否支持
     */
    boolean supports(DataSourceInfoEntity dsEntity);

    /**
     * 顺序执行多段SQL, 通过 callback 回吐过程事件.
     *
     * @param dsEntity  数据源
     * @param sqls      已切分清理后的SQL列表
     * @param callback  事件回调
     * @param cancelled 取消信号, 返回 true 表示已请求取消
     */
    void execute(DataSourceInfoEntity dsEntity,
                 List<String> sqls,
                 SqlExecutionCallback callback,
                 BooleanSupplier cancelled);

    /**
     * 尝试停止某个底层实例, 默认空实现.
     *
     * @param dsEntity   数据源
     * @param instanceId 引擎实例ID
     */
    default void stopInstance(DataSourceInfoEntity dsEntity, String instanceId) {
    }
}

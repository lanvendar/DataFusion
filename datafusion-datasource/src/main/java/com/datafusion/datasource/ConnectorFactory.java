package com.datafusion.datasource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源工厂.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/18
 * @since 2021/11/25
 */
@Slf4j
public class ConnectorFactory {

    /**
     * 默认数据源 key.
     */
    public static final String DEFAULT_DATA_SOURCE = "_defaultDataSource_";

    /**
     * 默认临时数据源 key.
     */
    public static final String TEMP_TEST_DATA_SOURCE = "_tempTestDataSource_";

    /**
     * 动态数据源信息.
     */
    @Getter
    private final Map<String, DataSourceInfo> dataSourceInfos = new ConcurrentHashMap<>();

    /**
     * 动态数据源连接.
     */
    private final Map<String, Connector> connectorMap = new ConcurrentHashMap<>();

    /**
     * 初始化动态数据源. 注:数据源使用时加载见本类 addDataSourcesConnect 方法.
     *
     * @param dataSourceInfoList 数据源配置对象 list
     */
    public ConnectorFactory(List<DataSourceInfo> dataSourceInfoList) {
        //初始化数据源
        addDataSources(dataSourceInfoList);
    }

    /**
     * 初始化动态数据源. 注:数据源使用时加载见本类 addDataSourcesConnect 方法.
     *
     * @param defaultDataSource  默认数据源
     * @param dataSourceInfoList 数据源配置对象 list
     */
    public ConnectorFactory(DataSourceInfo defaultDataSource, List<DataSourceInfo> dataSourceInfoList) {
        //初始化默认数据源
        addDataSource(defaultDataSource);
        //初始化数据源
        addDataSources(dataSourceInfoList);
    }

    /**
     * 添加临时数据源.
     *
     * @param tempDataSource 数据源配置对象
     */
    public void addTempDataSource(DataSourceInfo tempDataSource) {
        if (tempDataSource.getId() == null) {
            tempDataSource.setId(UUID.nameUUIDFromBytes(TEMP_TEST_DATA_SOURCE.getBytes()));
        }
        addDataSource(tempDataSource);
    }

    /**
     * 删除临时数据源.
     */
    public void cleanTempDatasource() {
        String id = UUID.nameUUIDFromBytes(ConnectorFactory.TEMP_TEST_DATA_SOURCE.getBytes()).toString();
        if (dataSourceInfos.containsKey(id)) {
            closeDataSources(id);
        }
    }

    /**
     * 判断是否是临时数据源.
     *
     * @param dsId 数据源id
     * @return true:是临时数据源
     */
    public boolean isTempDataSource(String dsId) {
        String tempDsId = UUID.nameUUIDFromBytes(ConnectorFactory.TEMP_TEST_DATA_SOURCE.getBytes()).toString();
        return tempDsId.equals(dsId);
    }

    /**
     * 添加数据源.
     *
     * @param dataSource 数据源配置对象
     */
    public void addDataSource(DataSourceInfo dataSource) {
        if (dataSource != null && dataSource.getId() != null) {
            dataSourceInfos.putIfAbsent(dataSource.getId().toString(), dataSource);
        }
    }

    /**
     * 批量添加数据源.
     *
     * @param dataSourceInfoList 数据源配置对象 list
     */
    public void addDataSources(List<DataSourceInfo> dataSourceInfoList) {
        UUID defaultId = UUID.nameUUIDFromBytes(ConnectorFactory.DEFAULT_DATA_SOURCE.getBytes());
        if (CollectionUtil.isNotEmpty(dataSourceInfoList)) {
            for (DataSourceInfo dataSourceInfo : dataSourceInfoList) {
                // 如果没有默认数据源,复制第一个数据源为默认数据源
                if (null == dataSourceInfos.get(defaultId.toString())) {
                    DataSourceInfo defaultDataSource = new DataSourceInfo();
                    defaultDataSource.setId(defaultId);
                    BeanUtil.copyProperties(dataSourceInfo, defaultDataSource);
                    addDataSource(defaultDataSource);
                }
                addDataSource(dataSourceInfo);
            }
        }
    }

    /**
     * 获取数据库连接.
     *
     * @param dataSourceId 数据源配置对象 id
     * @return 数据库连接
     */
    public Connector getConnector(String dataSourceId) {
        Connector connector = connectorMap.get(dataSourceId);
        if (connector != null) {
            return connector;
        }

        DataSourceInfo info = dataSourceInfos.get(dataSourceId);
        if (info == null) {
            log.error("数据源不存在: {}, 请先添加注册!", dataSourceId);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源不存在");
        }

        // 使用双重检查锁定模式（DCL）来创建单例连接,只有在connector为null时才进入同步块,减少锁竞争
        synchronized (this) {
            //在锁内,防止其他线程已经创建了连接
            connector = connectorMap.get(dataSourceId);
            if (connector == null) {
                // 如果连接仍然不存在,现在可以安全地创建它
                connector = createConnector(info);
                connectorMap.put(dataSourceId, connector);
            }
        }
        return connector;
    }

    /**
     * 创建数据库连接,并加进连接池.
     *
     * @param dataSourceInfo 数据源配置对象
     */
    private Connector createConnector(DataSourceInfo dataSourceInfo) {
        log.info("为数据源ID: {} 创建新的连接...", dataSourceInfo.getId());
        DatabaseTypeEnum type = DatabaseTypeEnum.fromString(dataSourceInfo.getDatabaseType());
        //连接信息有,连接没有则创建连接
        switch (type) {
            case POSTGRES:
                return new PostgresJdbcConnector(dataSourceInfo);
            case STARROCKS:
                return new MysqlJdbcConnect(dataSourceInfo);
            case MAXCOMPUTE:
                return new MaxcomputeJdbcConnector(dataSourceInfo);
            case HOLOGRES:
                return new PostgresJdbcConnector(dataSourceInfo);
            case DM:
                return new DmJdbcConnector(dataSourceInfo);
            /*case CASSANDRA:
                return new CassandraDataBaseConnect(dataSourceInfo);
            case PAIMON:
                return new PaimonDataBaseConnect(dataSourceInfo);
            case HIVE:
                return new HiveDataBaseConnect(dataSourceInfo);
            case MYSQL:
                return new MysqlDataBaseConnect(dataSourceInfo);
            case ORACLE:
                return new OracleDataBaseConnect(dataSourceInfo);
            case SQLSERVER:
                return new SqlServerDataBaseConnect(dataSourceInfo);
            case KINGBASE:
                return new KingBaseDataBaseConnect(dataSourceInfo);
            case GREENPLUM:
                return new GreenplumDataBaseConnect(dataSourceInfo);*/
            default:
                throw new CommonException(ErrorCodeEnum.USER_ERROR_A0400, "Invalid DatabaseTypeEnum: " + dataSourceInfo.getDatabaseType());
        }
    }

    /**
     * 关闭指定数据源.
     *
     * @param dataSourceId 数据源配置对象 id
     */
    public void closeDataSources(String dataSourceId) {
        dataSourceInfos.remove(dataSourceId);
        Connector connector = connectorMap.remove(dataSourceId);

        if (connector != null) {
            try {
                connector.destroy();
            } catch (Exception e) {
                log.error("关闭数据源 {} 的连接时发生错误", dataSourceId, e);
            }
        }
    }

    /**
     * 关闭所有数据源.
     */
    private void closeDataSources() {
        Set<String> dataSourceIds = new HashSet<>(connectorMap.keySet());
        for (String dataSourceId : dataSourceIds) {
            closeDataSources(dataSourceId);
        }
    }

    /**
     * 动态数据源刷新.
     *
     * @param dataSourceInfo 数据源配置对象
     * @return true - 数据源配置发生变化；false - 数据源配置未变化
     */
    public boolean checkIsChanged(DataSourceInfo dataSourceInfo) {
        if (dataSourceInfo == null) {
            return false;
        }
        DataSourceInfo original = dataSourceInfos.get(dataSourceInfo.getId().toString());
        return !dataSourceInfo.equals(original);
    }

    /**
     * 动态数据源刷新.
     *
     * @param dataSourceInfo 数据源配置对象
     */
    public synchronized void refreshDataSources(DataSourceInfo dataSourceInfo) {
        // 优化点: refreshDataSources 作为一个整体操作，保持同步是合理的。
        // 但内部调用已经优化。
        if (dataSourceInfo == null || dataSourceInfo.getId() == null) {
            return;
        }

        String dsId = dataSourceInfo.getId().toString();

        // 1. 关闭并移除旧的
        Connector oldConnector = connectorMap.remove(dsId);
        if (oldConnector != null) {
            try {
                oldConnector.destroy();
            } catch (Exception e) {
                log.error("刷新数据源时，关闭旧连接 {} 失败", dsId, e);
            }
        }

        // 2. 更新信息并重新创建（懒加载）
        dataSourceInfos.put(dsId, dataSourceInfo);
        // 不再需要立即创建连接，getConnector 会在下次需要时自动创建。
        log.info("数据源 {} 已被刷新，将在下次使用时创建新连接。", dsId);
    }
}

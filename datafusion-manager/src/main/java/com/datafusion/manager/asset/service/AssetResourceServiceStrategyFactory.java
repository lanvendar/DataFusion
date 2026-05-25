package com.datafusion.manager.asset.service;

import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * resource服务策略工厂.
 * 根据资源类型获取对应的导入策略.
 *
 * @author zhengjiexiang
 * @version 1.0.0, 2026/02/03
 * @since 2026/02/03
 */
@Component
public class AssetResourceServiceStrategyFactory {

    /**
     * 所有策略实现列表.
     */
    private final List<BaseResourceService> strategies;

    /**
     * 构造函数.
     *
     * @param strategies 策略实现列表，Spring自动注入所有实现
     */
    @Autowired
    public AssetResourceServiceStrategyFactory(List<BaseResourceService> strategies) {
        this.strategies = strategies;
    }

    /**
     * 根据资源类型获取对应的导入策略.
     *
     * @param resourceType 资源类型枚举
     * @return 对应的导入策略
     * @throws UnsupportedOperationException 如果没有找到对应的策略
     */
    public BaseResourceService getStrategy(ResourceTypeEnum resourceType) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(resourceType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "不支持的资源类型: " + resourceType));
    }

}

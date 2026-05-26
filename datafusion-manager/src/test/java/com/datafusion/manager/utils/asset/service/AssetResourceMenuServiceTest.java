package com.datafusion.manager.utils.asset.service;

import com.datafusion.manager.asset.dao.MenuTmpMapper;
import com.datafusion.manager.asset.service.AssetResourceApiService;
import com.datafusion.manager.asset.service.AssetResourceMenuService;
import com.datafusion.manager.asset.service.SkywalkingTraceProcessingService;
import com.datafusion.manager.asset.service.TraceGraphBuilder;
import com.datafusion.manager.asset.service.UnmatchedMenuRecorder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * AssetResourceMenuService 测试类.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class AssetResourceMenuServiceTest {

    @Autowired
    private AssetResourceMenuService assetResourceMenuService;

    /**
     * 注入真实的 UnmatchedMenuRecorder，用于触发持久化
     */
    @Autowired
    private UnmatchedMenuRecorder unmatchedMenuRecorder;

    /**
     * 注入真实的 MenuTmpMapper
     */
    @Autowired
    private MenuTmpMapper menuTmpMapper;

    /**
     * 注入真实的 AssetResourceApiService
     */
    @Autowired
    private AssetResourceApiService apiResourceService;

    /**
     * 注入真实的 AssetResourceApiService
     */
    @Autowired
    private SkywalkingTraceProcessingService skywalkingTraceProcessingService;

    @Autowired
    private TraceGraphBuilder traceGraphBuilder;

    /**
     * 注意：不 Mock AssetResourceMapper，让真实查询执行
     */

    /**
     * 测试 syncMenu 方法 - 不传 recorder，只记录日志.
     */
    @Test
    void testSyncMenuWithoutRecorder() {
        log.info("=== 测试开始（不传 recorder）===");
        assetResourceMenuService.syncMenu();
        log.info("=== 测试完成 ===");
    }

    /**
     * 测试 syncMenu 方法 - 传入 recorder，持久化到数据库.
     */
    @Test
    void testSyncMenuWithRecorder() {
        log.info("=== 测试开始（传入 recorder）===");
        assetResourceMenuService.syncMenu(unmatchedMenuRecorder);
        log.info("=== 测试完成 ===");
    }

    @Test
    void testNormalizeWeLocation() {
        log.info("=== 测试开始（传入 recorder）===");
        log.info(traceGraphBuilder.normalizeWeLocation("/storage-energy-pcs/2005896689327669248/4125KEAB259L0571"));
        log.info(traceGraphBuilder.normalizeWeLocation("/storage-energy-pcs/2001897708176146432/Store_PCS"));
        log.info("=== 测试完成 ===");
    }

}

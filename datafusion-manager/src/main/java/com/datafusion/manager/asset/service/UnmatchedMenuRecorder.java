package com.datafusion.manager.asset.service;

import com.datafusion.manager.asset.dao.MenuTmpMapper;
import com.datafusion.manager.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 未匹配菜单记录器.
 * 如果注入此服务则持久化到数据库，否则只记录日志.
 */
@Slf4j
@Service
public class UnmatchedMenuRecorder {

    @Autowired(required = false)
    private MenuTmpMapper menuTmpMapper;

    /**
     * 记录未匹配的菜单.
     *
     * @param unmatchedCombos   (projectCode, weLocation) 组合列表
     * @param apiResourceIds   对应的 API 资源 ID 列表
     */
    public void record(List<Pair<String, String>> unmatchedCombos, List<UUID> apiResourceIds) {
        if (unmatchedCombos == null || unmatchedCombos.isEmpty()) {
            return;
        }

        // 如果没有注入 menuTmpMapper，只记录日志
        if (menuTmpMapper == null) {
            for (int i = 0; i < unmatchedCombos.size(); i++) {
                Pair<String, String> combo = unmatchedCombos.get(i);
                log.info("未匹配菜单(projectCode={}, weLocation={}, apiResourceId={})",
                        combo.getLeft(), combo.getRight(),
                        i < apiResourceIds.size() ? apiResourceIds.get(i) : null);
            }
            return;
        }

        // 持久化到数据库
        String currentUser = HttpUtils.getCurrentUserName();
        Date now = new Date();

        for (int i = 0; i < unmatchedCombos.size(); i++) {
            Pair<String, String> combo = unmatchedCombos.get(i);
            String projectCode = combo.getLeft();
            String weLocation = combo.getRight();
            UUID apiResourceId = i < apiResourceIds.size() ? apiResourceIds.get(i) : null;

            try {
                menuTmpMapper.insertUnmatchedMenu(
                        UUID.randomUUID(),
                        projectCode,
                        weLocation,
                        apiResourceId,
                        currentUser,
                        now,
                        currentUser,
                        now
                );
                log.debug("记录未匹配菜单: projectCode={}, weLocation={}", projectCode, weLocation);
            } catch (Exception e) {
                log.warn("记录未匹配菜单失败: projectCode={}, weLocation={}", projectCode, weLocation, e);
            }
        }
    }
}
package com.datafusion.manager.asset.service;

import com.datafusion.manager.asset.po.AssetLineageResourceEntity;

import java.nio.file.Path;
import java.util.List;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
public interface EtlProcessService {
    
    /**
     * etl脚本处理.
     *
     * @param sqlFile gitlab 脚本路径
     * @return 返回文件收集到的资源
     */
    List<AssetLineageResourceEntity> gitLabFileProcess(Path sqlFile);
    
    /**
     * etl脚本处理.
     *
     * @param filePath 文件相对路经
     * @param fileName 文件相对路经
     * @return 返回文件收集到的资源
     */
    List<AssetLineageResourceEntity> gitLabFileProcess(String filePath, String fileName);
    
    /**
     * 更新整个etl资源.
     *
     * @return 返回所有符合条件的gitlab文件路径
     */
    Boolean getGitlabFiles();

    /**
     * 获取gitlab文件到本地，获取本地路径集合.
     *
     * @return 获取所有符合条件的gitlab文件路径
     */
    List<Path> getGitlabFilesPath();

    /**
     * 获取本地项目路径.
     *
     * @return 本地项目路径
     */
    String getLocalRepoBaseDir();
    
}

package com.datafusion.manager.asset.job;

import com.datafusion.manager.asset.dto.EtlSqlUpAndDownVo;
import com.datafusion.manager.asset.service.AssetResourceEtlService;
import com.datafusion.manager.asset.service.EtlProcessService;
import com.datafusion.manager.utils.AliyunOssUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 定时拉取gitlab的etl任务.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
@Slf4j
@Component
public class EtlGitLabJob {

    /**
     * 处理etl脚本service.
     */
    @Autowired
    private EtlProcessService etlProcessService;

    /**
     * ETL资源服务.
     */
    @Autowired
    private AssetResourceEtlService assetResourceEtlService;

    @Autowired
    private AliyunOssUtils aliyunOssUtils;

    /**
     * 拉取gitlab的文件.
     */
    //@Scheduled(fixedRate = 1000000)
    public void etlPull(EtlSqlUpAndDownVo req) {
        log.info("git pull begin excute");
         if (etlProcessService.getGitlabFiles()) {
             aliyunOssUtils.uploadDirectory(null, req.getUploadLocalPath(), req.getOssfilePrefixPath());
             log.info("git pull excute end");
         }

    }

    /**
     * 遍历文件夹，并导入sql文件到资源表
     */
    public void initEtlSql(EtlSqlUpAndDownVo req){
            aliyunOssUtils.downloadDirectoryFromOss(null, etlProcessService.getLocalRepoBaseDir(), "secp-dolphin-job-script/DAG/we/sebu1/");
            List<Path> sqlFiles = etlProcessService.getGitlabFilesPath();
//            if (CollectionUtil.isNotEmpty(sqlFiles)) {
//                for (Path sqlFile : sqlFiles) {
//                    // 解析脚本，并且生成
//                    List<AssetLineageResourceEntity> resources = etlProcessService.gitLabFileProcess(sqlFile);
//                    //assetResourceEtlService.distinctSaveBatch(resources);
//                    assetResourceEtlService.distinctSaveBatchNew(resources);
//                }
//            }
    }

}

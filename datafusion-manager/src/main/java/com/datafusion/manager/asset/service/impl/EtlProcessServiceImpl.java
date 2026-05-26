package com.datafusion.manager.asset.service.impl;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.asset.constant.AssetLineageConstant;
import com.datafusion.manager.asset.dto.EtlSnapshot;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.service.EtlProcessService;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.utils.GitUtils;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlProcessServiceImpl implements EtlProcessService {
    
    /**
     * gitlab的数仓的项目地址.
     */
    @Value("${git.url}")
    private String gitUrl;
    
    /**
     * gitlab的分支.
     */
    @Value("${git.branch}")
    private String branch;
    
    /**
     * gitlab的用户.
     */
    @Value("${git.username}")
    private String username;

    /**
     * gitlab的用户密码.
     */
    @Value("${git.password}")
    private String password;

    /**
     * hologres数据源Id.
     */
    @Value("${etl.hologres.datasourceId}")
    private String hologresDatasourceId;
    
    /**
     * maxcompute数据源Id.
     */
    @Value("${etl.maxcompute.datasourceId}")
    private String maxcomputeDatasourceId;

    /**
     * ETL 文件路径过滤规则-必须包含的仓库标识.
     */
    @Value("${etl.path.requiredRepoPath}")
    private String requiredRepoPath;

    /**
     * 本地仓库的根目录.
     */
    @Value("${etl.localRepoBaseDir.linux}")
    private  String localRepoBaseDirLinux;

    /**
     * 本地仓库的根目录.
     */
    @Value("${etl.localRepoBaseDir.win}")
    private  String localRepoBaseDirWin;


    /**
     * 本地git仓库的根目录.
     */
    @Value("${etl.localGitRepoBaseDir.linux}")
    private  String localGitRepoBaseDirLinux;

    /**
     * 本地git仓库的根目录.
     */
    @Value("${etl.localGitRepoBaseDir.win}")
    private  String localGitRepoBaseDirWin;
    /**
     * ETL 文件路径过滤规则-允许的仓库标识.
     */
    @Value("#{'${etl.path.allowed-paths}'.split(',')}")
    private List<String> allowedPaths;

    /**
     * 本地的项目路径.
     */
    public String getLocalRepoBaseDir() {
        return System.getProperty("os.name").toLowerCase().contains("linux")
                ? localRepoBaseDirLinux
                : localRepoBaseDirWin;
    }

    /**
     * 本地的git项目路径.
     */
    private String getLocalGitRepoBaseDir() {
        return System.getProperty("os.name").toLowerCase().contains("linux")
                ? localGitRepoBaseDirLinux
                : localGitRepoBaseDirWin;
    }

    /**
     * 数据源service.
     */
    @Autowired
    private DataSourceInfoService dataSourceInfoService;


    // gitlab文件处理
    @Override
    public List<AssetLineageResourceEntity> gitLabFileProcess(Path filePath) {
        log.info("git pull begin excute" + filePath);

        // 1. 数据源初始化与路径校验
        List<AssetLineageResourceEntity> resourceEntityList = new ArrayList<>();
        DataSourceInfoDto maxcomputeSource = dataSourceInfoService.getDataSource(UUID.fromString(maxcomputeDatasourceId));
        DataSourceInfoDto holoSource = dataSourceInfoService.getDataSource(UUID.fromString(hologresDatasourceId));
        if (!validateDataSources(maxcomputeSource, holoSource, resourceEntityList)) {
            return resourceEntityList;
        }

        // 2. 文件路径校验
        String filePathStr = extractFilePathStr(filePath);
        if (!validateFilePath(filePathStr, resourceEntityList)) {
            return resourceEntityList;
        }

        // 3. 解析SQL文件并构建资源实体列表
        File sqlFile = new File(filePath.toUri());
        return parseSqlFile(sqlFile, filePathStr, maxcomputeSource, holoSource);
    }

    //clone or pull from gitlab
    @Override
    public List<AssetLineageResourceEntity> gitLabFileProcess(String filePath, String fileName) {
        Path localRepoPath = Paths.get(getLocalRepoBaseDir(), AssetLineageConstant.PROJECT_NAME);
        File localRepoDir = localRepoPath.toFile();
        try {
            if (!localRepoDir.exists() || !new File(localRepoDir, GitUtils.GIT_DEFAULT_SUFFIX).exists()) {
                GitUtils.cloneRepository(localRepoDir, gitUrl, branch, username, password);
            } else {
                GitUtils.pullRepository(localRepoDir, branch, username, password);
            }
        } catch (Exception e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "init或更新仓库失败");
        }
        return gitLabFileProcess(Path.of(getLocalRepoBaseDir(), AssetLineageConstant.PROJECT_NAME, filePath, fileName));
    }

    @Override
    public Boolean getGitlabFiles() {
        Path localRepoPath = Paths.get(getLocalGitRepoBaseDir(), AssetLineageConstant.PROJECT_NAME);
        File localRepoDir = localRepoPath.toFile();
        try {
            if (!localRepoDir.exists() || !new File(localRepoDir, GitUtils.GIT_DEFAULT_SUFFIX).exists()) {
                GitUtils.cloneRepository(localRepoDir, gitUrl, branch, username, password);
            } else {
                GitUtils.pullRepository(localRepoDir, branch, username, password);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }


    @Override
    public List<Path> getGitlabFilesPath() {
        try {
            Path localRepoPath = Paths.get(getLocalRepoBaseDir(), AssetLineageConstant.PROJECT_NAME);
            return Files.walk(localRepoPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> (p.toString().toLowerCase().endsWith(AssetLineageConstant.MAXCOMPUTE_SCRIPT_SUFFIX)
                            || p.toString().toLowerCase().endsWith(AssetLineageConstant.SQL_SCRIPT_SUFFIX))
                            && !p.toString().toLowerCase().contains("ddl")
                            && !p.toString().toLowerCase().contains("-ignore."))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 校验数据源是否有效.
     *
     * @param maxcomputeSource MaxCompute数据源
     * @param holoSource Holo数据源
     * @param resourceEntityList 资源列表
     * @return 数据源是否有效
     */
    private boolean validateDataSources(DataSourceInfoDto maxcomputeSource, DataSourceInfoDto holoSource,
                                        List<AssetLineageResourceEntity> resourceEntityList) {
        if (maxcomputeSource == null || holoSource == null) {
            return false;
        }
        return true;
    }

    /**
     * 从文件路径提取相对路径字符串.
     *
     * @param filePath 文件路径
     * @return 相对路径字符串
     */
    private String extractFilePathStr(Path filePath) {
        String tmpFilePath = filePath.toString();
        int begin = tmpFilePath.indexOf(AssetLineageConstant.PROJECT_NAME) + AssetLineageConstant.PROJECT_NAME.length();
        int end = tmpFilePath.lastIndexOf(File.separator);
        return tmpFilePath.substring(begin, end);
    }

    /**
     * 校验文件路径是否符合规则（必须包含requiredRepoPath，且在allowedPaths中）.
     *
     * @param filePathStr 文件相对路径
     * @param resourceEntityList 资源列表
     * @return 路径是否有效
     */
    private boolean validateFilePath(String filePathStr, List<AssetLineageResourceEntity> resourceEntityList) {
        if (!filePathStr.contains(requiredRepoPath)) {
            return false;
        }
        for (String path : allowedPaths) {
            if (filePathStr.endsWith(path)) {
                return true;
            }
        }
        resourceEntityList.clear();
        return false;
    }

    /**
     * 解析SQL文件，构建资源实体列表.
     *
     * @param sqlFile SQL文件
     * @param filePathStr 文件相对路径
     * @param maxcomputeSource MaxCompute数据源
     * @param holoSource Holo数据源
     * @return 资源实体列表
     */
    private List<AssetLineageResourceEntity> parseSqlFile(File sqlFile, String filePathStr,
                                                          DataSourceInfoDto maxcomputeSource,
                                                          DataSourceInfoDto holoSource) {
        List<AssetLineageResourceEntity> resourceEntityList = new ArrayList<>();
        StringBuilder currentSb = null;
        String dagName = null;
        String taskName = null;
        String taskType = null;
        String sqlType = null;
        String dataSourceType = null;
        HashMap<String, Integer> taskNameMap = new HashMap<>();
        AssetLineageResourceEntity currentEntity = null;
        EtlSnapshot etlSnapshot = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 1. 解析头部信息（dag_name首次出现时）
                if (dagName == null) {
                    if (line.contains("--dag_name")) {
                        dagName = extractValueAfterColon(line);
                    } else {
                        return resourceEntityList;
                    }
                }

                // 2. 解析任务类型
                if (line.contains("--task_type")) {
                    taskType = extractValueAfterColon(line);
                }

                // 3. 解析数据源类型
                if (line.contains("--data_source_type")) {
                    dataSourceType = extractValueAfterColon(line);
                }

                // 4. 解析SQL类型
                if (line.contains("--sql_type")) {
                    sqlType = extractValueAfterColon(line);
                }

                // 5. 遇到新task_name，处理上一个任务
                if (line.contains("task_name")) {
                    if (taskName != null) {
                        // 保存上一个任务
                        finishCurrentTask(currentEntity, etlSnapshot, currentSb, dataSourceType,
                                maxcomputeSource, holoSource, resourceEntityList, taskType, sqlType);
                    }
                    // 创建新任务
                    currentEntity = createNewTask(line, taskNameMap, filePathStr, sqlFile, dagName);
                    taskName = extractValueAfterColon(line);
                    etlSnapshot = buildEtlSnapshot(dagName, taskName, filePathStr, sqlFile.getName());
                    currentSb = new StringBuilder();
                    dataSourceType = null;
                }

                // 6. 跳过不需要处理的行
                if (shouldSkipLine(line)) {
                    continue;
                }

                // 7. 处理SQL行（临时表后缀替换）
                line = processSqlLine(line);
                if (StringUtils.isNotEmpty(line.trim())) {
                    currentSb.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            log.error("读取SQL文件失败", e);
            return resourceEntityList;
        }

        // 8. 处理最后一个任务
        if (etlSnapshot != null && currentSb != null) {
            processLastTask(etlSnapshot, currentSb, dataSourceType, maxcomputeSource, holoSource,
                    resourceEntityList, taskType, sqlType, currentEntity);
        }

        return resourceEntityList;
    }

    /**
     * 从行中提取冒号后的值.
     *
     * @param line 原始行
     * @return 提取的值
     */
    private String extractValueAfterColon(String line) {
        return line.substring(line.lastIndexOf(":") + 1).trim();
    }

    /**
     * 判断当前行是否需要跳过.
     *
     * @param line 当前行
     * @return 是否跳过
     */
    private boolean shouldSkipLine(String line) {
        // 跳过注释行
        if (line.startsWith("--") || (line.toLowerCase().startsWith("set") && !line.toLowerCase().trim().endsWith("set"))) {
            return true;
        }
        // 跳过DROP语句
        if (line.toLowerCase().startsWith("drop")) {
            return true;
        }

        // 跳过ANALYZE语句
        if (line.toLowerCase().startsWith("analyze") && !line.trim().toLowerCase().endsWith("analyze")) {
            return true;
        }

        // 跳过odpscmd命令
        if (line.toLowerCase().contains("/opt/odpscmd/bin/odpscmd")) {
            return true;
        }
        return false;
    }

    /**
     * 处理SQL行（临时表后缀替换）.
     *
     * @param line 原始行
     * @return 处理后的行
     */
    private String processSqlLine(String line) {
        if (line.contains(AssetLineageConstant.TMP_TABLE_SUFFIX)) {
            line = line.replace(AssetLineageConstant.TMP_TABLE_SUFFIX, AssetLineageConstant.TMP_TABLE_SUFFIX_VALUE);
        }
        return line;
    }

    /**
     * 构建ETL快照对象.
     *
     * @param dagName DAG名称
     * @param taskName 任务名称
     * @param filePathStr 文件路径
     * @param fileName 文件名
     * @return ETL快照对象
     */
    private EtlSnapshot buildEtlSnapshot(String dagName, String taskName, String filePathStr, String fileName) {
        EtlSnapshot snapshot = new EtlSnapshot();
        snapshot.setDagName(dagName)
                .setTaskName(taskName)
                .setFilePath(filePathStr)
                .setFileName(fileName);
        return snapshot;
    }

    /**
     * 创建新任务实体.
     *
     * @param line 包含task_name的行
     * @param taskNameMap 任务名称计数器映射
     * @param filePathStr 文件相对路径
     * @param sqlFile SQL文件
     * @param dagName DAG名称
     * @return 资源实体
     */
    private AssetLineageResourceEntity createNewTask(String line, HashMap<String, Integer> taskNameMap,
                                                      String filePathStr, File sqlFile, String dagName) {
        String taskName = extractValueAfterColon(line);

        // 处理同一个文件出现相同的task_name的情况
        if (taskNameMap.containsKey(taskName)) {
            Integer taskNameCount = taskNameMap.get(taskName) + 1;
            taskNameMap.put(taskName, taskNameCount);
            taskName = taskName + taskNameCount;
        } else {
            taskNameMap.put(taskName, 0);
        }

        AssetLineageResourceEntity entity = new AssetLineageResourceEntity();
        entity.setResourceName(String.join(":", dagName, taskName))
                .setResourceType(ResourceTypeEnum.ETL.getResouceType())
                .setResourceTag(ResourceTagEnum.EDGE.getResourceTagType())
                .setStatus(ResourceStatusEnum.PARSE_SUCCESS.getStatus())
                .setId(UUID.randomUUID())
                .setCreateTime(new Date())
                .setUpdateTime(new Date())
                .setUpdater(HttpUtils.getCurrentUserName())
                .setCreator(HttpUtils.getCurrentUserName());

        return entity;
    }

    /**
     * 完成当前任务处理.
     */
    private void finishCurrentTask(AssetLineageResourceEntity currentEntity, EtlSnapshot etlSnapshot,
                                   StringBuilder currentSb, String dataSourceType,
                                   DataSourceInfoDto maxcomputeSource, DataSourceInfoDto holoSource,
                                   List<AssetLineageResourceEntity> resourceEntityList,
                                   String taskType, String sqlType) {
        etlSnapshot.setSql(currentSb.toString());
        applyDatasourceConfig(etlSnapshot, dataSourceType, maxcomputeSource, holoSource);
        currentEntity.setResourceSnapshot(new ObjectMapper().valueToTree(etlSnapshot));

        if (shouldAddResource(etlSnapshot, taskType, sqlType)) {
            resourceEntityList.add(currentEntity);
        }
    }

    /**
     * 处理最后一个任务.
     */
    private void processLastTask(EtlSnapshot etlSnapshot, StringBuilder currentSb, String dataSourceType,
                                  DataSourceInfoDto maxcomputeSource, DataSourceInfoDto holoSource,
                                  List<AssetLineageResourceEntity> resourceEntityList,
                                  String taskType, String sqlType, AssetLineageResourceEntity currentEntity) {
        String sql = currentSb.toString();
        if (StringUtils.isNotEmpty(sql) && StringUtils.isNotEmpty(sql.trim())) {
            if ("init_db".equals(taskType.toLowerCase()) && "1".equals(sqlType)) {
                applyDatasourceConfig(etlSnapshot, dataSourceType, maxcomputeSource, holoSource);
                etlSnapshot.setSql(sql.trim());
                currentEntity.setResourceSnapshot(new ObjectMapper().valueToTree(etlSnapshot));
                resourceEntityList.add(currentEntity);
            }
        }
    }

    /**
     * 判断是否应该添加到资源列表.
     */
    private boolean shouldAddResource(EtlSnapshot etlSnapshot, String taskType, String sqlType) {
        if (StringUtils.isEmpty(etlSnapshot.getSql()) || StringUtils.isEmpty(etlSnapshot.getSql().trim())) {
            return false;
        }
        // SQL任务且sql_type为1，或Shell任务
        return ("init_db".equals(taskType.toLowerCase()) && "1".equals(sqlType))
                || "shell".equals(taskType.toLowerCase());
    }

    /**
     * 应用数据源配置.
     */
    private void applyDatasourceConfig(EtlSnapshot etlSnapshot, String dataSourceType,
                                        DataSourceInfoDto maxcomputeSource, DataSourceInfoDto holoSource) {
        if (dataSourceType != null && dataSourceType.equals("postgresql")) {
            etlSnapshot.setDatasourceName(holoSource.getName())
                    .setDatasourceId(holoSource.getId())
                    .setDatabaseType(holoSource.getDatabaseType())
                    .setDatabaseName(holoSource.getDatabaseName())
                    .setSchemaName(holoSource.getSchemaName());
        } else {
            etlSnapshot.setDatasourceName(maxcomputeSource.getName())
                    .setDatasourceId(maxcomputeSource.getId())
                    .setDatabaseType(maxcomputeSource.getDatabaseType())
                    .setDatabaseName(maxcomputeSource.getDatabaseName())
                    .setSchemaName(maxcomputeSource.getSchemaName());
        }
    }

}

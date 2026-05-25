package com.datafusion.manager.utils;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

/**
 * Git 工具类.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
@Slf4j
public class GitUtils {

    /**
     * git默认后缀.
     */
    public static final String GIT_DEFAULT_SUFFIX = ".git";

    /**
     * 克隆 GitLab 项目至本地.
     * @param localPath 本地路径
     * @param remoteUrl  远程仓库地址
     * @param branch  分支名称
     * @param username 用户名
     * @param password 密码
     */
    public static void cloneRepository(File localPath, String remoteUrl, String branch, 
                                       String username, String password) throws GitAPIException {
        // 参数验证
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("远程仓库地址不能为空");
        }
        if (localPath == null) {
            throw new IllegalArgumentException("本地路径不能为空");
        }

        // 检查目录是否已存在
        if (localPath.exists()) {
            if (localPath.isDirectory() && localPath.list() != null && localPath.list().length > 0) {
                log.warn("目标目录已存在且非空，先删除: {}", localPath.getAbsolutePath());
                deleteDirectory(localPath);
            }
        }

        // 创建父目录
        if (!localPath.getParentFile().exists()) {
            localPath.getParentFile().mkdirs();
        }

        // 执行 clone
        try {
            Git git = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(localPath)
                    .setBranch(branch)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .setCloneAllBranches(false)
                    .setTimeout(300)
                    .call();
            
            log.info("Git 仓库克隆成功: {}", localPath.getAbsolutePath());
            git.close();
        } catch (GitAPIException e) {
            log.error("克隆 Git 仓库失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 拉取 GitLab 项目更新.
     * @param localPath 本地路径
     * @param branch  分支名称
     * @param username 用户名
     * @param password 密码
     */
    public static void pullRepository(File localPath, String branch, String username, String password)
            throws IOException, GitAPIException {
        
        // 验证目录
        if (!localPath.exists() || !localPath.isDirectory()) {
            throw new IllegalArgumentException("本地仓库路径不存在: " + localPath.getAbsolutePath());
        }

        File gitDir = new File(localPath, GIT_DEFAULT_SUFFIX);
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("不是 Git 仓库: " + localPath.getAbsolutePath());
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .build();
                Git git = new Git(repository)) {
            
            // 检查并切换分支
            String currentBranch = repository.getBranch();
            if (!currentBranch.equals(branch)) {
                log.info("切换分支: {} -> {}", currentBranch, branch);
                git.checkout().setName(branch).call();
            }
            // 清理未跟踪的文件和 reset 本地修改
            git.clean().setCleanDirectories(true).setForce(true).call();
            git.reset()
                    .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                    .setRef("HEAD")
                    .call();
            // 执行 pull
            PullResult pullResult = git.pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .setTimeout(300)
                    .call();

            // 处理结果
            if (pullResult.isSuccessful()) {
                log.info("Git 仓库拉取成功: {}", localPath.getAbsolutePath());
            } else {
                log.error("Git pull 失败 - Merge状态: {}, 冲突: {}", 
                        pullResult.getMergeResult() != null ? pullResult.getMergeResult().getMergeStatus() : "N/A",
                        pullResult.getMergeResult() != null ? pullResult.getMergeResult().getConflicts() : "N/A");
                throw new GitAPIException("Git pull 失败") { };
            }
        } catch (Exception e) {
            log.error("拉取 Git 仓库失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 同步 Git 仓库（自动判断 clone 或 pull）.
     *
     * @param localPath 本地仓库目录
     * @param remoteUrl 远程仓库地址
     * @param branch 分支名称
     * @param username 用户名
     * @param password 密码
     */
    public static void syncRepo(File localPath, String remoteUrl, String branch,
                                String username, String password) throws GitAPIException, IOException {
        File gitDir = new File(localPath, GIT_DEFAULT_SUFFIX);
        if (!localPath.exists() || !gitDir.exists()) {
            cloneRepository(localPath, remoteUrl, branch, username, password);
        } else {
            pullRepository(localPath, branch, username, password);
        }
    }

    /**
     * 递归删除目录.
     */
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
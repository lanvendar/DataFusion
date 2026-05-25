package com.datafusion.common.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.template.source.JarFileSource;
import com.jfinal.template.source.FileSource;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Sql 模板 扫描器.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/04/11
 * @since 2022/04/11
 */
@Slf4j
public class SqlSourceScanner {
    /**
     * 扫描器.
     *
     * @return 扫描器
     */
    public static Scanner scan() {
        return new Scanner();
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Scanner {
        /**
         * 扫描源.
         */
        private final Set<ScanSource> sources = new HashSet<>();
        
        /**
         * sql 模板文件后缀.
         */
        private String suffix = ".sql";
        
        /**
         * 扫描 ClassPath 的 SQL 目录的.
         *
         * @param relativePath 相对路径
         * @return 扫描器
         */
        public Scanner fromClasspath(String relativePath) {
            sources.add(new ClasspathScanSource(relativePath));
            return this;
        }
        
        /**
         * 扫描文件目录的 SQL 目录的.
         *
         * @param absoluteDirectoryPath 绝对路径
         * @return 扫描器
         */
        public Scanner fromDirectory(String absoluteDirectoryPath) {
            sources.add(new DirectoryScanSource(absoluteDirectoryPath));
            return this;
        }
        
        /**
         * 扫描 jar 包中的 SQL 目录的.
         *
         * @param jarPath      jar 包路径
         * @param internalPath jar 包中的内部路径
         * @return 扫描器
         */
        public Scanner fromJar(String jarPath, String internalPath) {
            sources.add(new JarScanSource(jarPath, internalPath));
            return this;
        }
        
        /**
         * 设置文件后缀.
         *
         * @param suffix 文件后缀
         * @return 扫描器
         */
        public Scanner withSuffix(String suffix) {
            this.suffix = suffix;
            return this;
        }
        
        /**
         * 执行扫描.
         *
         * @return 扫描结果
         */
        public Set<SqlSource> execute() {
            Set<SqlSource> allSources = new HashSet<>();
            log.info("开始执行SQL模板扫描，文件后缀: {}", suffix);
            for (ScanSource source : sources) {
                try {
                    allSources.addAll(source.scan(suffix));
                } catch (Exception e) {
                    log.error("扫描源 [{}] 时发生错误", source.toString(), e);
                }
            }
            log.info("SQL模板扫描完成，共找到 {} 个源。", allSources.size());
            return allSources;
        }
    }
    
    /**
     * 扫描源接口.
     */
    private interface ScanSource {
        /**
         * 扫描.
         *
         * @param suffix 文件后缀
         * @return 扫描结果
         */
        Set<SqlSource> scan(String suffix);
    }
    
    /**
     * 扫描源实现类.
     */
    @RequiredArgsConstructor
    private static class ClasspathScanSource implements ScanSource {
        /**
         * 相对路径.
         */
        private final String relativePath;
        
        @Override
        public Set<SqlSource> scan(String suffix) {
            Set<SqlSource> sqlSources = new HashSet<>();
            if (StrUtil.isBlank(relativePath)) {
                return sqlSources;
            }
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                String path = relativePath.replace('.', '/');
                Enumeration<URL> resources = classLoader.getResources(path);
                while (resources.hasMoreElements()) {
                    URL resourceUrl = resources.nextElement();
                    if ("file".equals(resourceUrl.getProtocol())) {
                        File directory = new File(URLDecoder.decode(resourceUrl.getPath(), StandardCharsets.UTF_8));
                        scanDirectoryForClasspath(directory, path, suffix, sqlSources);
                    } else if ("jar".equals(resourceUrl.getProtocol())) {
                        scanJarForClasspath(resourceUrl, path, suffix, sqlSources);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("扫描Classpath失败: " + relativePath, e);
            }
            return sqlSources;
        }
        
        /**
         * 扫描目录下的文件.
         *
         * @param dir         目录
         * @param currentPath 当前路径
         * @param suffix      文件后缀
         * @param sources     扫描结果
         */
        private void scanDirectoryForClasspath(File dir, String currentPath, String suffix, Set<SqlSource> sources) {
            for (File file : FileUtil.loopFiles(dir)) {
                if (file.getName().endsWith(suffix)) {
                    String subPath = FileUtil.subPath(dir.getAbsolutePath(), file);
                    String resourcePath = (currentPath + "/" + subPath).replace(File.separatorChar, '/');
                    sources.add(new SqlSource(resourcePath));
                }
            }
        }
        
        /**
         * 扫描 jar 包下的文件.
         *
         * @param jarUrl      jar 包 URL
         * @param currentPath 当前路径
         * @param suffix      文件后缀
         * @param sources     扫描结果
         */
        private void scanJarForClasspath(URL jarUrl, String currentPath, String suffix, Set<SqlSource> sources) throws IOException {
            JarFile jarFile = ((JarURLConnection) jarUrl.openConnection()).getJarFile();
            String prefix = currentPath + "/";
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(suffix) && !entry.isDirectory()) {
                    sources.add(new SqlSource(name));
                }
            }
        }
        
        @Override
        public String toString() {
            return "ClasspathSource(path=" + relativePath + ")";
        }
    }
    
    /**
     * 扫描源实现类.
     */
    @RequiredArgsConstructor
    private static class DirectoryScanSource implements ScanSource {
        /**
         * 目录路径.
         */
        private final String absoluteDirectoryPath;
        
        @Override
        public Set<SqlSource> scan(String suffix) {
            Set<SqlSource> sqlSources = new HashSet<>();
            File baseDir = new File(absoluteDirectoryPath);
            if (!baseDir.isDirectory()) {
                log.warn("提供的外部目录路径不存在或不是一个目录: {}", absoluteDirectoryPath);
                return sqlSources;
            }
            for (File file : FileUtil.loopFiles(baseDir)) {
                if (file.getName().endsWith(suffix)) {
                    String relativePath = FileUtil.subPath(absoluteDirectoryPath, file).replace(File.separatorChar, '/');
                    sqlSources.add(new SqlSource(new FileSource(absoluteDirectoryPath, relativePath)));
                }
            }
            return sqlSources;
        }
        
        @Override
        public String toString() {
            return "DirectorySource(path=" + absoluteDirectoryPath + ")";
        }
    }
    
    /**
     * 扫描源实现类.
     */
    @RequiredArgsConstructor
    private static class JarScanSource implements ScanSource {
        /**
         * JAR 文件路径.
         */
        private final String jarPath;
        
        /**
         * 内部路径.
         */
        private final String internalPath;
        
        @Override
        public Set<SqlSource> scan(String suffix) {
            Set<SqlSource> sqlSources = new HashSet<>();
            File jarFile = new File(jarPath);
            if (!jarFile.isFile()) {
                log.warn("提供的JAR文件路径不存在或不是一个文件: {}", jarPath);
                return sqlSources;
            }
            try (JarFile jar = new JarFile(jarFile)) {
                String prefix = StrUtil.removeSuffix(internalPath.replace('.', '/'), "/") + "/";
                jar.entries().asIterator().forEachRemaining(entry -> {
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(suffix)) {
                        sqlSources.add(new SqlSource(new JarFileSource(jarPath, name)));
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("处理JAR文件失败: " + jarPath, e);
            }
            return sqlSources;
        }
        
        @Override
        public String toString() {
            return "JarSource(path=" + jarPath + ", internalPath=" + internalPath + ")";
        }
    }
}
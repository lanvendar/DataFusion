package com.datafusion.common.template.source;

import com.jfinal.template.EngineConfig;
import com.jfinal.template.source.ClassPathSource;
import com.jfinal.template.source.ISource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 自定义的 ISource 实现，用于从指定的物理 JAR 文件中加载模板资源.
 * 设计风格参考自 JFinal 的 ClassPathSource{@link ClassPathSource}
 *
 * @author lanvendar
 * @version 1.0.0 ,2025/06/20
 * @since 2025/06/20
 */
public class JarFileSource implements ISource {
    /**
     * JAR包的物理文件路径.
     */
    protected final String jarPath;
    
    /**
     * 模板在JAR包内的路径.
     */
    protected final String innerFileName;
    
    /**
     * 文件编码.
     */
    protected final String encoding;
    
    /**
     * JAR文件对象.
     */
    protected final File jarFile;
    
    /**
     * JAR文件的最后修改时间，用于热加载判断.
     */
    protected long lastModified;
    
    /**
     * 构造方法.
     *
     * @param jarPath       JAR包的物理文件路径
     * @param innerFileName 模板在JAR包内的路径
     */
    public JarFileSource(String jarPath, String innerFileName) {
        this(jarPath, innerFileName, EngineConfig.DEFAULT_ENCODING);
    }
    
    /**
     * 构造方法.
     *
     * @param jarPath       JAR包的物理文件路径
     * @param innerFileName 模板在JAR包内的路径, e.g., "sql/example.sql"
     * @param encoding      文件编码
     */
    public JarFileSource(String jarPath, String innerFileName, String encoding) {
        this.jarPath = jarPath;
        // 规范化内部路径
        this.innerFileName = buildFinalEntryName(innerFileName);
        this.encoding = encoding;
        this.jarFile = new File(jarPath);
        
        // 在构造函数中进行严格的检查，提前失败
        if (!jarFile.exists() || !jarFile.isFile()) {
            throw new IllegalArgumentException("JAR file not found: \"" + jarPath + "\"");
        }
        
        this.lastModified = this.jarFile.lastModified();
    }
    
    /**
     * 规范化 entryName，确保不以'/'开头.
     *
     * @param innerFileName 模板在JAR包内的路径
     * @return 规范后的 entryName
     */
    private String buildFinalEntryName(String innerFileName) {
        if (innerFileName == null || innerFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Entry name in JAR file can not be blank.");
        }
        // JFinal ClassPathSource 也是去掉开头的'/'
        if (innerFileName.charAt(0) == '/' || innerFileName.charAt(0) == '\\') {
            return innerFileName.substring(1);
        }
        return innerFileName;
    }
    
    @Override
    public boolean isModified() {
        // 热加载逻辑：检查物理JAR文件的修改时间
        return lastModified != jarFile.lastModified();
    }
    
    @Override
    public String getCacheKey() {
        // 使用JAR文件绝对路径和条目路径共同组成一个唯一的、全局的缓存Key
        // 模仿 ClassPathSource，它使用 fileName 作为 key。这里我们用组合 key
        return jarFile.getAbsolutePath() + "!" + innerFileName;
    }
    
    @Override
    public String getEncoding() {
        return this.encoding;
    }
    
    @Override
    public StringBuilder getContent() {
        // 在获取内容前，更新修改时间，用于下一次 isModified() 判断
        if (isModified()) {
            this.lastModified = jarFile.lastModified();
        }
        
        try (JarFile jar = new JarFile(this.jarFile)) {
            JarEntry entry = jar.getJarEntry(this.innerFileName);
            if (entry == null) {
                // 如果在打开后发现条目不存在，抛出运行时异常
                throw new RuntimeException("Entry not found in JAR file: \"" + this.innerFileName + "\" (in " + this.jarPath + ")");
            }
            
            try (InputStream inputStream = jar.getInputStream(entry)) {
                // 复用官方的 loadFile 方法，保持一致性
                return ClassPathSource.loadFile(inputStream, encoding);
            }
        } catch (IOException e) {
            // 将受检异常包装成运行时异常，与JFinal风格一致
            throw new RuntimeException("Failed to load content from JAR file: " + getCacheKey(), e);
        }
    }
    
    @Override
    public String toString() {
        return "JarFileSource{" + "jarPath='" + jarPath + '\'' + ", entryName='" + innerFileName + '\'' + ", lastModified=" + lastModified + '}';
    }
}
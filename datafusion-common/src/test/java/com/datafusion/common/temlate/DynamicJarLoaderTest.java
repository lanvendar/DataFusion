package com.datafusion.common.temlate;


import com.datafusion.common.template.SqlTemplate;
import com.datafusion.common.template.ext.directive.DateCalDirective;
import com.datafusion.common.template.ext.directive.NameSpaceDirective;
import com.datafusion.common.template.ext.directive.PlaceHolderDirective;
import com.datafusion.common.template.ext.directive.SqlDirective;
import com.datafusion.common.template.source.JarFileSource;
import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lanvendar
 * @version 1.0.0, 2025/6/19
 * @since 2025/6/19
 */
@Slf4j
public class DynamicJarLoaderTest {
    private static final String TEST_SQL_JAR = resolveTestResourcePath("sqlLoadPath/test-sql.jar");

    @Test
    public void testLoadFromJarInTestClasses() throws Exception {
        // 1. 创建并设置我们自定义的 SourceFactory
        Engine engine = Engine.use();
        engine.setDevMode(false);
        //设置sql模板根路径,资源类路径的相对路径
        engine.setBaseTemplatePath(null);
        //设置项目 classpath 路径
        engine.setToClassPathSourceFactory();
        //是否开启sql压缩功能
        engine.setCompressorOn(' ');
        //加载扩展函数
        engine.addDirective("namespace", NameSpaceDirective.class);
        engine.addDirective("sql", SqlDirective.class);
        engine.addDirective("day", DateCalDirective.class);
        
        engine.addDirective("ps", PlaceHolderDirective.class, true);
        // 2. 定义指向你的目标 JAR 文件和内部模板的特殊路径
        String jarFilePath = TEST_SQL_JAR;
        String entryPath = "sql/test1.sql"; // 开头的'/'是可选的，我们的工厂会处理
        JarFileSource customSource = new JarFileSource(jarFilePath, entryPath);
        
        // 3. 使用这个特殊路径获取模板
        Template template = engine.getTemplate(customSource);
        Map<String, SqlTemplate> sqlTemplateMap = new HashMap<>(512, 0.5F);
        // 4. 渲染模板
        // 假设 test1.sql 的内容是: select * from user where name = #para(name)
        Map<Object, Object> data = new HashMap<>();
        
        data.put(SqlDirective.SQL_TEMPLATE_MAP_KEY, sqlTemplateMap);
        
        template.renderToString(data);
        for (Map.Entry<String, SqlTemplate> entry : sqlTemplateMap.entrySet()){
            log.info(entry.getKey() + ": " + entry.getValue().getTemplate());
        }
    }
    @Test
    public void jarPathTest(){
        // 1. 定义你的 JAR 包的路径
        // 这个路径来自 test classpath，不依赖本地工作目录
        String jarRelativePath = TEST_SQL_JAR;
        File jarFile = new File(jarRelativePath);
        
        // 2. 检查 JAR 文件是否存在
        if (!jarFile.exists()) {
            System.err.println("错误：指定的 JAR 文件不存在 -> " + jarFile.getAbsolutePath());
            return;
        }
        
        System.out.println("准备从 JAR 文件加载资源: " + jarFile.getAbsolutePath());
        
        // 3. 定义要从 JAR 包内部加载的资源路径
        String resourceInsideJar = "sql/test1.sql";
        
        // 4. 【核心步骤】创建 URLClassLoader 并加载资源
        URL resourceUrl = null;
        // 使用 try-with-resources 确保 ClassLoader 在使用后可以被关闭 (如果是 Java 7+ 的 URLClassLoader)
        // 注意：URLClassLoader 实现了 Closeable 接口，所以可以这样做。
        try {
            // 将 File 对象转换为 URL。必须使用 .toURI().toURL() 以正确处理路径。
            URL jarUrl = jarFile.toURI().toURL();
            
            // 创建一个新的 URLClassLoader，它的搜索路径只包含我们指定的这个 JAR 包
            URLClassLoader jarClassLoader = new URLClassLoader(new URL[]{jarUrl});
            
            // 使用这个新的、专门的类加载器去加载资源
            resourceUrl = jarClassLoader.getResource(resourceInsideJar);
            
            // 使用完毕后，最好关闭它以释放文件句柄
            jarClassLoader.close();
            
        } catch (IOException e) {
            System.err.println("创建类加载器或加载资源时出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        
        // 5. 检查结果
        if (resourceUrl != null) {
            System.out.println("\n成功！找到了资源！");
            System.out.println("资源的 URL 是: " + resourceUrl);
            // 输出会是: jar:file:/.../test-sql.jar!/sql/test1.sql
        } else {
            System.out.println("\n失败！在指定的 JAR 包中未找到资源: " + resourceInsideJar);
        }
    }

    private static String resolveTestResourcePath(String resourcePath) {
        URL resource = DynamicJarLoaderTest.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Test resource not found: " + resourcePath);
        }
        try {
            return Path.of(resource.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid test resource path: " + resourcePath, e);
        }
    }
}

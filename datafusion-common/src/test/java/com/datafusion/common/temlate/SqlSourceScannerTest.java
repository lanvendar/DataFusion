package com.datafusion.common.temlate;

import com.datafusion.common.template.SqlSource;
import com.datafusion.common.template.SqlSourceScanner;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

/**
 * 单元测试 类
 * @author lanvendar
 * @version 1.0.0, 2025/6/20
 * @since 2025/6/20
 */
@Slf4j
public class SqlSourceScannerTest {
    private static final String SQL_LOAD_PATH = resolveTestResourcePath("sqlLoadPath");
    private static final String TEST_SQL_JAR = Path.of(SQL_LOAD_PATH, "test-sql.jar").toString();

    @Test
    public void testFindSqlSourceInClassPath() {
        SqlSourceScanner.Scanner scanner = SqlSourceScanner.scan()//
                .fromClasspath("sql")//
                .withSuffix(".sql");
        Set<SqlSource> sqlSources = scanner.execute();
        log.info("sqlSources: {}", sqlSources);
    }
    @Test
    public void testFindSqlSourceInDirectory() {
        SqlSourceScanner.Scanner scanner = SqlSourceScanner.scan()//
                .fromClasspath("sql")//
                .fromDirectory(SQL_LOAD_PATH)
                .withSuffix(".sql");
        Set<SqlSource> sqlSources = scanner.execute();
        log.info("sqlSources: {}", sqlSources);
    }
    @Test
    public void testFindSqlSourceInJar() {
        SqlSourceScanner.Scanner scanner = SqlSourceScanner.scan()//
                .fromJar(TEST_SQL_JAR, "sql")//
                .withSuffix(".sql"); Set<SqlSource> sqlSources = scanner.execute();
        log.info("sqlSources: {}", sqlSources);
    }

    private static String resolveTestResourcePath(String resourcePath) {
        URL resource = SqlSourceScannerTest.class.getClassLoader().getResource(resourcePath);
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

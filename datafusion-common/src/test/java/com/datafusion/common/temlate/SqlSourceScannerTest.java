package com.datafusion.common.temlate;

import com.datafusion.common.template.SqlSource;
import com.datafusion.common.template.SqlSourceScanner;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * 单元测试 类
 * @author lanvendar
 * @version 1.0.0, 2025/6/20
 * @since 2025/6/20
 */
@Slf4j
public class SqlSourceScannerTest {
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
                .fromDirectory("D:\\IdeaProjects\\datafusion\\datafusion-common\\src\\test\\resources\\sqlLoadPath")
                .withSuffix(".sql");
        Set<SqlSource> sqlSources = scanner.execute();
        log.info("sqlSources: {}", sqlSources);
    }
    @Test
    public void testFindSqlSourceInJar() {
        SqlSourceScanner.Scanner scanner = SqlSourceScanner.scan()//
                .fromJar("D:\\IdeaProjects\\datafusion\\datafusion-common\\src\\test\\resources\\sqlLoadPath\\test-sql.jar","sql")//
                .withSuffix(".sql"); Set<SqlSource> sqlSources = scanner.execute();
        log.info("sqlSources: {}", sqlSources);
    }
}

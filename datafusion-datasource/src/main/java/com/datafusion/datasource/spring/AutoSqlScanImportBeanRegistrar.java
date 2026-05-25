package com.datafusion.datasource.spring;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 实现了Spring的 {@link ImportBeanDefinitionRegistrar} 接口,
 * 用于响应 {@link SqlScan} 注解，并启动自定义的类路径扫描.
 *
 * @author lanvendar
 * @version 1.0.0, 2021/3/13
 * @since 2021/3/13
 **/
public class AutoSqlScanImportBeanRegistrar implements ImportBeanDefinitionRegistrar {
    
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获取MapperScan注解属性信息
        AnnotationAttributes annotationAttributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(SqlScan.class.getName()));
        // 获取注解的属性值,拿到定义的扫描路径
        if (annotationAttributes != null) {
            String[] basePackages = annotationAttributes.getStringArray("basePackages");
            if (basePackages.length > 0) {
                // 使用自定义扫描器扫描
                AutoSqlScanClassPathBeanScanner scanner = new AutoSqlScanClassPathBeanScanner(registry, false);
                scanner.scan(basePackages);
            }
        }
    }
}


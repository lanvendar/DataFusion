package com.datafusion.datasource.spring;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 扫描类并将其实例化注入IOC容器.
 *
 * @author lanvendar
 * @version 1.0.0, 2021/3/13
 * @since 2021/3/13
 **/
public class AutoSqlScanClassPathBeanScanner extends ClassPathBeanDefinitionScanner {
    
    /**
     * 构造方法.
     *
     * @param registry          {@code BeanFactory} 以 {@code BeanDefinitionRegistry} 的形式加载 bean 定义
     * @param useDefaultFilters 是否包含 Spring 默认组件过滤器
     */
    public AutoSqlScanClassPathBeanScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
        super(registry, useDefaultFilters);
        // 重置过滤器，然后添加我们自己的逻辑
        resetFilters(false);
        addIncludeFilter(new MapperInterfaceFilter());
    }
    
    /**
     * description: 负责对接口代理进行定义.
     *
     * @param basePackages basePackages
     * @return void
     */
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        // 使用自定义的复合过滤器
        Set<BeanDefinitionHolder> beanDefinitionHolders = super.doScan(basePackages);
        if (!beanDefinitionHolders.isEmpty()) {
            processBeanDefinitions(beanDefinitionHolders);
        }
        return beanDefinitionHolders;
    }
    
    /**
     * 负责对接口代理进行定义.
     *
     * @param beanDefinitionHolderSet 接口代理集合
     */
    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitionHolderSet) {
        beanDefinitionHolderSet.forEach(holder -> {
            // 设置工厂等操作需要基于GenericBeanDefinition，BeanDefinitionHolder是其子类
            GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
            // 获取接口的全路径名称
            String beanClassName = definition.getBeanClassName();
            // 为FactoryBean的构造函数提供参数（即被代理的接口）
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            // 将Bean的类设置为我们的代理工厂
            definition.setBeanClass(SqlMapperProxyFactory.class);
            // 允许按类型自动装配，如果工厂需要其他依赖
            definition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
        });
    }
    
    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        //覆盖父类方法，确保我们只考虑接口
        return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
    }
    
    /**
     * 一个复合类型过滤器，用于匹配满足以下任一条件的接口.
     * 1. 被 {@link SqlRepository} 注解
     * 2. 实现了 {@link SqlMapper} 接口
     */
    private static class MapperInterfaceFilter implements TypeFilter {
        /**
         * 匹配注解.
         */
        private final TypeFilter annotationFilter = new AnnotationTypeFilter(SqlRepository.class);
        
        /**
         * 匹配接口.
         */
        private final TypeFilter interfaceFilter = new AssignableTypeFilter(SqlMapper.class);
        
        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
            return annotationFilter.match(metadataReader, metadataReaderFactory)
                    || interfaceFilter.match(metadataReader, metadataReaderFactory);
        }
    }
}

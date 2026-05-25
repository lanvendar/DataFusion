package com.datafusion.common.spring.utils;

import com.datafusion.common.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * springboot bean 外部调用对象.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/21
 * @since 2021/11/24
 */
@Slf4j
@Component
public class ApplicationContextUtil implements ApplicationContextAware {
    
    /**
     * springboot ApplicationContext 实例注册器.
     */
    private static volatile ApplicationContext applicationContext;
    
    /**
     * DCL方式设置ApplicationContext.
     *
     * @param ctx ApplicationContext
     */
    public static void setContext(ApplicationContext ctx) {
        if (applicationContext == null) {
            synchronized (ApplicationContextUtil.class) {
                if (applicationContext == null) {
                    applicationContext = ctx;
                }
            }
        }
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        setContext(applicationContext);
        log.info("[普通类可以通过调用SpringUtils.getAppContext()获取applicationContext对象]");
    }
    
    /**
     * 取得存储在静态变量中的ApplicationContext.
     *
     * @return ApplicationContext 对象
     */
    public static ApplicationContext getApplicationContext() {
        checkApplicationContext();
        return applicationContext;
    }
    
    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     *
     * @param name Bean名称
     * @param <T>  泛型
     * @return 泛型
     */
    public static <T> T getBean(String name) {
        checkApplicationContext();
        if (applicationContext.containsBean(name)) {
            return (T) applicationContext.getBean(name);
        }
        return null;
    }
    
    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     *
     * @param clazz 类对象
     * @param <T>   泛型
     * @return 泛型
     */
    public static <T> T getBean(Class<T> clazz) {
        checkApplicationContext();
        return (T) applicationContext.getBeansOfType(clazz);
    }
    
    /**
     * 检查是否注入.
     */
    private static void checkApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("applicaitonContext未注入,请在applicationContext.xml中定义SpringContextUtil");
        }
    }
    
    /**
     * 注册 bean 对象.
     *
     * @param beanName bean名称
     * @param clzz     类对象
     * @param original 类参数
     */
    public static synchronized void registerSingletonBean(String beanName, Class clzz, Map<String, Object> original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ApplicationContextUtil.getApplicationContext()
                .getAutowireCapableBeanFactory();
        if (beanFactory.containsBean(beanName)) {
            removeBean(beanName);
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(clzz);
        //属性赋值
        definition.setPropertyValues(new MutablePropertyValues(original));
        //注册到spring上下文
        beanFactory.registerBeanDefinition(beanName, definition);
    }
    
    /**
     * 注册 bean 对象.
     *
     * @param beanName bean名称
     * @param obj      对象
     * @param original 类参数
     */
    public static synchronized void registerSingletonBean(String beanName, Object obj, Map<String, Object> original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ApplicationContextUtil.getApplicationContext()
                .getAutowireCapableBeanFactory();
        if (beanFactory.containsBean(beanName)) {
            removeBean(beanName);
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(obj.getClass());
        //属性赋值
        definition.setPropertyValues(new MutablePropertyValues(original));
        //注册到spring上下文
        beanFactory.registerBeanDefinition(beanName, definition);
    }
    
    /**
     * 注册单例 Bean.
     *
     * @param beanName 单例名称
     * @param obj      单例对象
     */
    public static synchronized void registerSingletonBean(String beanName, Object obj) {
        registerSingletonBean(beanName, obj, JacksonUtils.tryStr2Bean(obj.toString(), Map.class));
    }
    
    /**
     * 删除spring中管理的bean.
     *
     * @param beanName bean名称
     */
    public static void removeBean(String beanName) {
        ApplicationContext ctx = ApplicationContextUtil.getApplicationContext();
        DefaultListableBeanFactory acf = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
        if (acf.containsBean(beanName)) {
            acf.removeBeanDefinition(beanName);
        }
    }
    
}

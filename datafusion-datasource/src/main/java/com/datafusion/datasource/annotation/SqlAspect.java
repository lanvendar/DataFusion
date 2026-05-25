package com.datafusion.datasource.annotation;

import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.template.SqlParamRender;
import com.datafusion.datasource.Connector;
import com.datafusion.datasource.ConnectorFactory;
import com.datafusion.datasource.model.ExecuteParam;
import com.datafusion.datasource.spring.AnnotatedProxy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 多数据源，切面处理类.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/18
 * @since 2021/10/14
 */
@Slf4j
@Aspect
public class SqlAspect {
    
    /**
     * jfinalSql模板解析器.
     */
    private final JFinalSqlBuilder sqlBuilder;
    
    /**
     * 动态数据源创建工厂.
     */
    private final ConnectorFactory connectorFactory;
    
    /**
     * 方法解析器.
     */
    private final MethodResolver resolver;
    
    /**
     * 构造方法.
     *
     * @param connectorFactory 动态数据源创建工厂
     */
    public SqlAspect(ConnectorFactory connectorFactory) {
        this(connectorFactory, new JFinalSqlBuilder());
    }
    
    /**
     * 构造方法.
     *
     * @param connectorFactory 动态数据源创建工厂
     * @param sqlBuilder       jfinalSql模板解析器
     */
    public SqlAspect(ConnectorFactory connectorFactory, JFinalSqlBuilder sqlBuilder) {
        this.connectorFactory = connectorFactory;
        this.sqlBuilder = sqlBuilder;
        this.resolver = new MethodResolver();
    }
    
    /**
     * 定义获取单条 sql path 的切点.
     */
    @Pointcut("@within(com.datafusion.datasource.annotation.SqlGet) || @annotation(com.datafusion.datasource.annotation.SqlGet)")
    public void sqlGet() {
    }
    
    /**
     * 动态数据源切面主增强方法 类似 mybaits 注解使用形式.
     *
     * @param point 切面对象
     * @return 返回查询结果集 Object
     */
    @Around("sqlGet()")
    public Object around(ProceedingJoinPoint point) {
        // 创建方法解析对象
        MethodResolverContext context = adaptContext(point);
        // 动态数据源,设置合适的数据源信息
        ExecuteParam executeParam = resolver.buildExecuteParam(context, connectorFactory);
        // 检查是否创建了临时数据源
        boolean isTempDs = connectorFactory.isTempDataSource(executeParam.getDsId());
        try {
            // 获取数据源连接对象
            Connector connector = connectorFactory.getConnector(executeParam.getDsId());
            // 获取sql模板路径
            String sqlPath = executeParam.getSqlKey();
            // 获取sql模板,处理无参,单条,多条,
            if (executeParam.isBatch()) {
                List<SqlParamRender> renders = sqlBuilder.renderSqlBatch(sqlPath, executeParam.getParams());
                executeParam.setRenders(renders);
            } else {
                SqlParamRender sqlParamRender = sqlBuilder.renderSql(sqlPath, executeParam.getParam());
                log.info("run sql:{}", sqlParamRender.getSql());
                executeParam.setRender(sqlParamRender);
            }
            // 执行sql
            return connector.execute(executeParam);
        } finally {
            // 确保临时数据源总是被清理
            if (isTempDs) {
                connectorFactory.cleanTempDatasource();
            }
        }
    }
    
    /**
     * 适配器方法：将Spring AOP的上下文安全地转换为框架内核所需的上下文.
     *
     * @param point Spring AOP的连接点
     * @return 框架内核所需的 {@link MethodResolverContext}
     * @throws IllegalStateException 如果被拦截的代理对象不是由本框架创建的。
     */
    private MethodResolverContext adaptContext(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 对于接口代理，直接从Signature获取方法可能更安全，因为它代表了接口上的方法
        Method method = signature.getMethod();
        Object[] args = point.getArgs();
        Object proxy = point.getThis();
        
        if (!(proxy instanceof AnnotatedProxy)) {
            // 如果一个被我们切点拦截的bean不符合我们的代理契约，这是一个严重的配置错误。
            // 快速失败是最好的选择。
            throw new IllegalStateException("被 @SqlGet 拦截的Bean [" + proxy.getClass().getName() + "] "
                    + "不符合框架管理契约.请确保该接口实现了 SqlMapper "
                    + "或被 @SqlRepository 注解,并通过 @SqlScan 机制进行管理.");
        }
        
        AnnotatedProxy annotatedProxy = (AnnotatedProxy) proxy;
        Class<?> targetClass = annotatedProxy.getTargetClass();
        Annotation[] targetAnnotations = annotatedProxy.getTargetClassAnnotations();
        
        return new MethodResolverContext(method, args, targetClass, targetAnnotations, proxy);
    }
}

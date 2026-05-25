package com.datafusion.datasource.annotation;

import cn.hutool.core.util.StrUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.ConnectorFactory;
import com.datafusion.datasource.model.ExecuteParam;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * 解析方法签名类.
 *
 * @author lanvendar 。
 * @version 1.0.0, 2024/6/20
 * @since 2022/4/25
 */
@Slf4j
public class MethodResolver {

    /**
     * SQL模板路径解析器.
     */
    private final SqlGetResolver sqlGetResolver;

    /**
     * SQL数据源解析器.
     */
    private final SqlDsResolver sqlDsResolver;

    /**
     * SQL参数解析器.
     */
    private final SqlParamResolver sqlParamResolver;

    /**
     * 构造方法.
     */
    public MethodResolver() {
        this.sqlGetResolver = new SqlGetResolver();
        this.sqlDsResolver = new SqlDsResolver();
        this.sqlParamResolver = new SqlParamResolver();
    }

    /**
     * 一次性遍历上下文，构建完整的执行参数对象.
     *
     * @param context 方法调用上下文
     * @param factory 用于解析动态数据源的连接器工厂
     * @return 一个完整的 ExecutionPlan 对象
     */
    public ExecuteParam buildExecuteParam(MethodResolverContext context, ConnectorFactory factory) {
        ExecuteParam executeParam = new ExecuteParam();

        // 1. 填充 SqlGet 相关信息
        sqlGetResolver.resolve(context, executeParam);

        // 循环遍历所有参数，分发给各个解析器
        Annotation[][] paramAnnotations = context.getParameterAnnotations();
        Object[] args = context.getArgs();
        String dsId = null;
        Map<String, Object> sqlParamMap = new HashMap<>();
        // 用于存放@SqlParams注解的参数值
        Object sqlParams = null;
        for (int i = 0; i < paramAnnotations.length; i++) {
            Object arg = args[i];
            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof SqlDs) {
                    // 解析 SqlDs 相关信息, 及处理数据源注册/开关
                    dsId = sqlDsResolver.resolve((SqlDs) annotation, arg, factory);
                } else if (annotation instanceof SqlParam) {
                    SqlParam param = (SqlParam) annotation;
                    if (StrUtil.isBlank(param.value())) {
                        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "param:" + param + "为空");
                    }
                    // 解析 SqlParam 相关信息
                    //Type paramType = context.getMethod().getGenericParameterTypes()[i];
                    sqlParamResolver.resolve(param.value(), arg, sqlParamMap);
                } else if (annotation instanceof SqlParams) {
                    if (sqlParams != null) {
                        log.warn("找到了多个 @SqlParams 注解, 只有第一个会被使用, 请检查方法签名.");
                    } else {
                        sqlParams = arg;
                    }
                }
                // 在这里可以为其他参数注解添加更多的 'else if'
            }
        }

        // 根据优先级逻辑，最终确定数据源ID
        sqlDsResolver.adaptFinalDsId(executeParam, context, dsId);
        // 根据优先级逻辑，最终确定SQL参数
        sqlParamResolver.adaptFinalSqlParams(executeParam, sqlParamMap, sqlParams);
        // 设置返回值类型
        executeParam.setReturnType(context.getMethod().getGenericReturnType());
        return executeParam;
    }
}

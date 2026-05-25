package com.datafusion.datasource.annotation;

import cn.hutool.core.util.StrUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.datasource.model.ExecuteParam;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * SqlGet注解解析器 {@link SqlGet}.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/25
 * @since 2025/7/25
 */
public class SqlGetResolver {
    /**
     * 解析方法上下文中的 @SqlGet 注解，并将结果填充到 executeParam 对象.
     *
     * @param context      方法调用的元信息上下文。
     * @param executeParam 用于填充解析结果的参数对象。
     */
    public void resolve(MethodResolverContext context, ExecuteParam executeParam) {
        Method method = context.getMethod();
        Class<?> targetClass = context.getTargetClass();
        
        // 1. 查找注解实例
        SqlGet methodAnnotation = method.getAnnotation(SqlGet.class);
        SqlGet classAnnotation = targetClass.getAnnotation(SqlGet.class);
        
        // 防御性检查：切点应该保证注解至少存在一个
        if (methodAnnotation == null && classAnnotation == null) {
            throw new IllegalStateException("Missing @SqlGet annotation on method or class: " + method.getName());
        }
        
        // 2. 确定 isBatch 模式
        // 规则：方法上的注解优先级高于类上的。
        boolean isBatch = (methodAnnotation != null) ? methodAnnotation.isBatch() : classAnnotation.isBatch();
        executeParam.setBatch(isBatch);
        
        // 3. 确定并拼接 sqlKey
        List<String> sqlKeyParts = new ArrayList<>();
        
        // 来自类注解
        if (classAnnotation != null && StrUtil.isNotBlank(classAnnotation.sqlKey())) {
            sqlKeyParts.add(classAnnotation.sqlKey());
        }
        
        // 来自方法注解或方法名
        if (methodAnnotation != null && StrUtil.isNotBlank(methodAnnotation.sqlKey())) {
            sqlKeyParts.add(methodAnnotation.sqlKey());
        } else {
            // 如果方法注解没有提供 sqlKey，则使用方法名作为托底
            sqlKeyParts.add(method.getName());
        }
        
        // 使用点号拼接所有部分
        String finalSqlKey = String.join(SystemConstant.POINT, sqlKeyParts);
        executeParam.setSqlKey(finalSqlKey);
    }
}

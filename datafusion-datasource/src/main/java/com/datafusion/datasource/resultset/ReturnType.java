package com.datafusion.datasource.resultset;

import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结果集返回类型.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
@Builder
@Getter
public class ReturnType {
    /**
     * 当前层原始、完整的 Type.
     */
    private final Type originalType;
    
    /**
     * 当前层类型的 Class.
     */
    private final Class<?> rawClass;
    
    /**
     * 嵌套下一层的泛型类型集合(因为泛型会有多个,所以是个集合).
     */
    private final List<ReturnType> genericParameters;
    
    /**
     * 判断是否是泛型类型.
     *
     * @return true:泛型类型;false:非泛型类型
     */
    public boolean isGeneric() {
        return genericParameters != null && !genericParameters.isEmpty();
    }
    
    /**
     * 重写 toString() 方法.
     *
     * @return 类型的字符串表示
     */
    @Override
    public String toString() {
        // 使用一个辅助方法来递归构建字符串
        return buildToString(new StringBuilder()).toString();
    }
    
    /**
     * 递归构建字符串表示.
     *
     * @param sb 构建字符串的 StringBuilder 对象
     * @return 构建后的 StringBuilder 对象
     */
    private StringBuilder buildToString(StringBuilder sb) {
        // 【优化】特殊处理数组类型
        if (rawClass.isArray()) {
            // 递归获取组件类型
            ReturnType componentType = this.genericParameters.get(0);
            componentType.buildToString(sb);
            sb.append("[]");
        } else {
            // 原始逻辑
            sb.append(rawClass.getName());
            if (isGeneric()) {
                sb.append("<");
                String params = genericParameters.stream()
                        .map(p -> p.buildToString(new StringBuilder()).toString())
                        .collect(Collectors.joining(", "));
                sb.append(params);
                sb.append(">");
            }
        }
        return sb;
    }
}

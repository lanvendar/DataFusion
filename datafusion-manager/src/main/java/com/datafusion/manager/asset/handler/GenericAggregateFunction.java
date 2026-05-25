package com.datafusion.manager.asset.handler;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.AggregateFunction;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.sql.type.SqlTypeName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用聚合函数包装类.
 * 可以将任意静态方法包装为 Calcite 的聚合函数.
 *
 * @author wei.bowen
 * @version 1.0.0
 * @since 2026/2/3
 */
public class GenericAggregateFunction implements AggregateFunction {

    /**
     * 要包装的方法.
     */
    private final Method method;

    /**
     * 函数名称.
     */
    private final String functionName;

    /**
     * 返回类型（可选，默认为 ANY）.
     */
    private final SqlTypeName returnType;

    /**
     * 构造函数 - 默认返回类型为 ANY.
     *
     * @param method 要包装的方法
     * @param functionName 函数名称
     */
    public GenericAggregateFunction(Method method, String functionName) {
        this(method, functionName, SqlTypeName.ANY);
    }

    /**
     * 构造函数 - 指定返回类型.
     *
     * @param method 要包装的方法
     * @param functionName 函数名称
     * @param returnType 返回类型
     */
    public GenericAggregateFunction(Method method, String functionName, SqlTypeName returnType) {
        this.method = method;
        this.functionName = functionName;
        this.returnType = returnType;
    }

    /**
     * 获取返回类型.
     */
    @Override
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) {
        // 根据返回类型创建合适的数据类型
        if (returnType == SqlTypeName.VARCHAR) {
            // 对于 VARCHAR 类型，指定最大长度，并设置为可空
            return typeFactory.createTypeWithNullability(
                    typeFactory.createSqlType(SqlTypeName.VARCHAR, 65535), // 指定最大长度
                    true  // 可空
            );
        } else if (returnType == SqlTypeName.ARRAY) {
            // 返回 ARRAY<VARCHAR> 类型，更符合 collect_set/collect_list 的语义
            RelDataType elementType = typeFactory.createSqlType(SqlTypeName.VARCHAR, 65535);
            RelDataType arrayType = typeFactory.createArrayType(elementType, -1);
            return typeFactory.createTypeWithNullability(arrayType, true);
        } else {
            // 其他类型（如 ANY）
            return typeFactory.createTypeWithNullability(
                    typeFactory.createSqlType(returnType),
                    true
            );
        }
    }

    /**
     * 获取参数类型.
     */
    @Override
    public List<FunctionParameter> getParameters() {
        List<FunctionParameter> params = new ArrayList<>();
        Class<?>[] paramTypes = method.getParameterTypes();

        for (int i = 0; i < paramTypes.length; i++) {
            final int ordinal = i;
            final String paramName = "arg" + i;

            params.add(new FunctionParameter() {
                @Override
                public int getOrdinal() {
                    return ordinal;
                }

                @Override
                public String getName() {
                    return paramName;
                }

                @Override
                public RelDataType getType(RelDataTypeFactory typeFactory) {
                    // 支持任意类型，包括时间类型
                    return typeFactory.createSqlType(SqlTypeName.ANY);
                }

                @Override
                public boolean isOptional() {
                    return false;
                }
            });
        }

        return params;
    }

    @Override
    public String toString() {
        return "GenericAggregateFunction{"
                + "functionName='" + functionName + '\''
                + ", returnType=" + returnType
                + ", method=" + method.getName()
                + '}';
    }
}
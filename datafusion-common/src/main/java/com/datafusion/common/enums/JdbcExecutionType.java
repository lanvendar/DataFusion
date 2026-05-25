package com.datafusion.common.enums;

import java.util.EnumSet;

/**
 * sql 执行类型.
 *
 * @author gzy
 * @version 1.0.0, 2022/4/25
 * @since 2022/4/25
 */
public enum JdbcExecutionType {
    /**
     * 基础执行 .
     */
    STATEMENT("statement"),
    /**
     * 预编译.
     */
    PREPARED("prepared"),
    /**
     * 调用存储过程.
     */
    CALLABLE("callable");
    
    /**
     * sql 执行类型.
     */
    private final String value;
    
    /**
     * 构造方法.
     *
     * @param value sql 执行类型
     */
    JdbcExecutionType(String value) {
        this.value = value;
    }
    
    @Override
    public String toString() {
        return this.value;
    }
    
    /**
     * 字符串转化枚举类型.
     *
     * @param value 目标字符串
     * @return 返回枚举
     */
    public static JdbcExecutionType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return EnumSet.allOf(JdbcExecutionType.class).stream().filter(s -> s.toString().equals(value)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid JdbcExecutionType: " + value));
    }
}

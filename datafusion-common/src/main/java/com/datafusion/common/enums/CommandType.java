package com.datafusion.common.enums;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * sql语句类型枚举.
 *
 * @author lanvendar
 * @version 1.0, 2021/12/30
 * @since 2021/12/30
 */
public enum CommandType {
    /**
     * 数据查询语言 DQL : 查询数据.
     */
    SELECT("select", Category.DQL),
    /**
     * 数据操作语言 DML : 插入数据.
     */
    INSERT("insert", Category.DML),
    /**
     * 数据操作语言 DML : 更新数据.
     */
    UPDATE("update", Category.DML),
    /**
     * 数据操作语言 DML : 删除数据.
     */
    DELETE("delete", Category.DML),
    /**
     * 数据定义语言 DDL : 定义型操作.
     * 例如 CREATE TABLE, CREATE VIEW 等.
     */
    CREATE("create", Category.DDL),
    /**
     * 数据定义语言 DDL : 修改型操作.
     * 例如 ALTER TABLE, ALTER VIEW 等.
     */
    ALTER("alter", Category.DDL),
    /**
     * 数据定义语言 DDL : 删除型操作.
     * 例如 DROP TABLE, DROP VIEW 等.
     */
    DROP("drop", Category.DDL),
    /**
     * 数据定义语言 DDL : 清空表.
     */
    TRUNCATE("truncate", Category.DDL),
    /**
     * 数据控制语言 DCL : 赋权操作.
     */
    GRANT("grant", Category.DCL);
    
    /**
     * 命令类型的分类.
     */
    public enum Category {
        /**
         * 数据查询语言 DQL : 查询数据.
         */
        DQL,
        /**
         * 数据操作语言 DML : 插入数据, 更新数据, 删除数据.
         */
        DML,
        /**
         * 数据定义语言 DDL : 定义型操作.
         * 例如 CREATE TABLE, CREATE VIEW 等.
         */
        DDL,
        /**
         * 数据控制语言 DCL : 控制用户访问权限.
         * 例如 GRANT, REVOKE 等.
         */
        DCL,
        /**
         * 其他.
         */
        OTHER
    }
    
    /**
     * 命令类型的值.
     */
    private final String value;
    
    /**
     * 命令类型的分类.
     */
    private final Category category;
    
    /**
     * 使用静态 Map 进行缓存, 实现 O(1) 时间复杂度的查找.
     */
    private static final Map<String, CommandType> VALUES_MAP =
            Stream.of(values()).collect(Collectors.toMap(CommandType::getValue, Function.identity()));
    
    /**
     * 命令类型值到枚举的映射.
     */
    CommandType(String value, Category category) {
        this.value = value;
        this.category = category;
    }
    
    /**
     * 获取命令类型的小写字符串值.
     *
     * @return a command string like "insert".
     */
    public String getValue() {
        return this.value;
    }
    
    /**
     * 获取命令的分类.
     *
     * @return the command category (DQL, DML, DDL, OTHER).
     */
    public Category getCategory() {
        return this.category;
    }
    
    /**
     * 判断是否为数据修改操作 (INSERT, UPDATE, DELETE).
     *
     * @return true if the command is modifying, false otherwise.
     */
    public boolean isModifying() {
        return this.category == Category.DML;
    }
    
    /**
     * 从字符串安全地转换为 CommandType 枚举.
     *
     * @param value a string like "insert".
     * @return the corresponding CommandType, or null if not found.
     */
    public static CommandType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return VALUES_MAP.get(value.toLowerCase());
    }
    
    /**
     * 从字符串转换为 CommandType 枚举, 如果找不到则抛出异常.
     *
     * @param value a string like "insert".
     * @return the corresponding CommandType.
     * @throws IllegalArgumentException if the value is invalid.
     */
    public static CommandType fromValueOrThrow(String value) {
        CommandType commandType = fromValue(value);
        if (commandType == null) {
            throw new IllegalArgumentException("Invalid CommandType value: " + value);
        }
        return commandType;
    }
}

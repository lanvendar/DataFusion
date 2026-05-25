package com.datafusion.manager.metadata.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公共Column.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/6
 * @since 3.7.2, 2024/11/6
 */
@Data
@Schema(name = "SelectListColumn", description = "列表查询字段")
public class SelectListColumn {
    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "字段名称", hidden = true)
    private String columnName;

    /**
     * 字段描述.
     */
    @Schema(name = "title", description = "字段名称")
    private String title;

    /**
     * 查询字段-java字段.
     */
    @Schema(name = "field", description = "字段key")
    private String field;

    /**
     * 构造函数.
     *
     * @param columnName 字段名称
     * @param columnDesc 字段描述
     */
    public SelectListColumn(String columnName, String columnDesc) {
        this.columnName = columnName;
        if (StringUtils.isNotEmpty(columnDesc)) {
            this.title = columnDesc;
        } else {
            this.title = columnName;
        }
       
        this.field = columnName; //toCamelCase(columnName);
    }
    
    /**
     * 构造函数.
     *
     * @param columnName 字段名称
     * @param columnDesc 字段描述
     * @param quotes 字段列名前后添加符号，如：`、'等
     */
    public SelectListColumn(String columnName, String columnDesc, String quotes) {
        this.columnName = String.format("%s%s%s", quotes, columnName, quotes);
        this.title = columnDesc;
        this.field = columnName; //toCamelCase(columnName);
    }
    
    /**
     * 设置列值时在值周围添加引号.
     * 这个方法用于处理需要在数据库查询中使用的列值，通过在值周围添加引号，
     * 可以确保值的完整性，并在某些情况下提高查询的准确性和安全性
     *
     * @param quotes 要设置的列值字符串，在设置到列之前，会在该字符串周围添加引号
     */
    public void setColumnWithQuotes(String quotes) {
        this.columnName = String.format("%s%s%s", quotes, columnName, quotes);
    }
    
    /**
     * 将数据库表字段名称转换为 Java 驼峰命名风格.
     *
     * @param columnName 数据库表字段名称
     * @return Java 驼峰命名风格的字段名称
     */
    public static String toCamelCase(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }

        // 使用正则表达式匹配下划线及其后的字符
        Pattern pattern = Pattern.compile("_(.)");
        Matcher matcher = pattern.matcher(columnName);

        // 使用 StringBuffer 来构建新的字符串
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(result);

        // 将第一个字符转为小写
        return Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }
}

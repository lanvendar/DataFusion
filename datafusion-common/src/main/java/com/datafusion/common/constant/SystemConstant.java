package com.datafusion.common.constant;

import java.util.regex.Pattern;

/**
 * 系统常量字符.
 *
 * @author lanvendar
 * @version 1.0.0 , 2022/02/09
 * @since 2021/11/23
 */
public class SystemConstant {
    /**
     * 私有构造函数,防止外部创建实例.
     */
    private SystemConstant() {
        throw new IllegalStateException("static constant class");
    }
    
    /**
     * 系统常量字符 "--".
     */
    public static final String COMMENT_SYMBOL = "--";
    
    /**
     * 系统常量字符 "_".
     */
    public static final String UNDER_LINE = "_";
    
    /**
     * 系统常量字符 ";".
     */
    public static final String SEMICOLON = ";";
    
    /**
     * 系统常量字符 ":".
     */
    public static final String COLON = ":";
    
    /**
     * 系统常量字符 "\n".
     */
    public static final String LINE_FEED = "\n";
    
    /**
     * 系统常量字符 "".
     */
    public static final String BLANK = "";
    
    /**
     * 系统常量字符 "/".
     */
    public static final String VIRGULE = "/";
    
    /**
     * 系统常量字符 ",".
     */
    public static final String COMMA = ",";
    
    /**
     * 系统常量字符 ".".
     */
    public static final String POINT = ".";
    
    /**
     * 系统常量字符 "->".
     */
    public static final String ARROW = "->";
    
    /**
     * 系统常量字符 "@".
     */
    public static final String CERTAIN_SERVER = "@";
    
    /**
     * 系统常量字符 "&".
     */
    public static final String AND_SERVER = "&";
    
    /**
     * 系统常量字符 "-".
     */
    public static final String MIDDLE_LINE = "-";
    
    /**
     * 问号占位符.
     */
    public static final String QUESTION_MARK = "?";
    
    /**
     * 系统常量字符 "http://".
     */
    public static final String HTTP_URL_PREFIX = "http://";
    
    /**
     * 正则表达式忽略大小和 * .
     */
    public static final int DEFAULT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    
    /**
     * MDC flow instance id.
     */
    public static final String MDC_FLOW_INSTANCE_ID = "flowInsId";
    
    /**
     * MDC task instance id.
     */
    public static final String MDC_TASK_INSTANCE_ID = "taskInsId";
    
    /**
     * 空格.
     */
    public static final String SPACE = " ";
    
    /**
     * 等于号字符串.
     */
    public static final String EQ = "=";
    
    /**
     * and字符串.
     */
    public static final String AND = "and";
    
    /**
     * null 字符串.
     */
    public static final String NULL = "null";
    
    /**
     * true 字符串.
     */
    public static final String TRUE = "true";
    
    /**
     * false 字符串.
     */
    public static final String FALSE = "false";
    
    /**
     * 系统常量字符 "`".
     */
    public static final String BACKTICK = "`";
    
    /**
     * 系统常量字符 "|".
     */
    public static final String PIPE = "|";
    
    /**
     * 系统常量字符 "*".
     */
    public static final String ASTERISK = "*";
    
    /**
     * 系统常量字符 "$".
     */
    public static final String DOLLAR = "$";
    
    /**
     * 系统常量字符单引号"'".
     */
    public static final String SINGLE_QUOTES = "'";
    
    /**
     * 系统常量字符两个单引号"''".
     */
    public static final String TWO_QUOTES = "''";
    
    /**
     * 系统常量字符左小括号"(".
     */
    public static final String LEFT_PARENTHESIS = "(";
    
    /**
     * 系统常量字符右小括号")".
     */
    public static final String RIGHT_PARENTHESIS = ")";
    
    /**
     * 一分钟毫秒数.
     */
    public static final Long ONE_MINUTE_MILLIS = 60 * 1000L;
    
    /**
     * 实例类型 - 流程.
     */
    public static final String INSTANCETYPE_FLOW = "flow";
    
    /**
     * 实例类型 - 任务.
     */
    public static final String INSTANCETYPE_TASK = "task";
}
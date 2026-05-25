package com.datafusion.scheduler.master.param.exp;

/**
 * 函数占位符处理工具类.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
public final class FuncPlaceholderUtils {

    /**
     * 占位符前缀.
     */
    public static final String PLACEHOLDER_PREFIX = "(";

    /**
     * 占位符后缀.
     */
    public static final String PLACEHOLDER_SUFFIX = ")";

    private FuncPlaceholderUtils() {
        // 私有构造函数，防止实例化
    }
}

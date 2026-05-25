package com.datafusion.scheduler.master.param;

/**
 * 占位符处理器接口.
 * 使用策略模式统一处理不同类型的占位符.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
public interface PlaceholderHandler {

    /**
     * 替换字符串中的占位符.
     *
     * @param value   包含占位符的字符串
     * @param context 占位符处理上下文
     * @return 替换后的字符串
     */
    String replacePlaceholders(String value, PlaceholderContext context);

    /**
     * 获取占位符前缀.
     *
     * @return 占位符前缀
     */
    String getPrefix();

    /**
     * 获取占位符后缀.
     *
     * @return 占位符后缀
     */
    String getSuffix();
}

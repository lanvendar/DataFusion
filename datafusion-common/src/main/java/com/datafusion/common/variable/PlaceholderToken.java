package com.datafusion.common.variable;

/**
 * 占位符 token.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class PlaceholderToken {

    /**
     * token 类型.
     */
    private final PlaceholderTokenType type;

    /**
     * 函数名或变量名.
     */
    private final String name;

    /**
     * 原始文本.
     */
    private final String rawText;

    /**
     * 参数文本.
     */
    private final String argumentsText;

    /**
     * 起始下标.
     */
    private final int startIndex;

    /**
     * 结束下标.
     */
    private final int endIndex;

    /**
     * 构造函数.
     *
     * @param type           token 类型
     * @param name           名称
     * @param rawText        原始文本
     * @param argumentsText   参数文本
     * @param startIndex     起始下标
     * @param endIndex       结束下标
     */
    public PlaceholderToken(PlaceholderTokenType type, String name, String rawText,
            String argumentsText, int startIndex, int endIndex) {
        this.type = type;
        this.name = name;
        this.rawText = rawText;
        this.argumentsText = argumentsText;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public PlaceholderTokenType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getRawText() {
        return rawText;
    }

    public String getArgumentsText() {
        return argumentsText;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }
}

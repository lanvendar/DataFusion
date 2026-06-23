package com.datafusion.common.variable;

import java.util.ArrayList;
import java.util.List;

/**
 * 占位符 tokenizer.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class PlaceholderTokenizer {

    /**
     * 扫描 token.
     *
     * @param value 文本
     * @return token 列表
     */
    public List<PlaceholderToken> scan(String value) {
        List<PlaceholderToken> tokens = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return tokens;
        }

        int index = 0;
        while (index < value.length()) {
            int start = value.indexOf('#', index);
            if (start < 0) {
                break;
            }
            if (start + 1 >= value.length()) {
                break;
            }

            char next = value.charAt(start + 1);
            if (next == '(') {
                int end = findClosingParen(value, start + 1);
                String rawText = value.substring(start, end + 1);
                String name = value.substring(start + 2, end).trim();
                tokens.add(new PlaceholderToken(PlaceholderTokenType.VARIABLE, name, rawText, null, start, end + 1));
                index = end + 1;
                continue;
            }
            if (!isFunctionNameStart(next)) {
                index = start + 1;
                continue;
            }

            int openParen = value.indexOf('(', start + 1);
            if (openParen < 0) {
                index = start + 1;
                continue;
            }
            int end = findClosingParen(value, openParen);
            String rawText = value.substring(start, end + 1);
            String name = value.substring(start + 1, openParen).trim();
            if (!isFunctionName(name)) {
                index = start + 1;
                continue;
            }
            String argumentsText = value.substring(openParen + 1, end);
            tokens.add(new PlaceholderToken(PlaceholderTokenType.FUNCTION, name, rawText,
                    argumentsText, start, end + 1));
            index = end + 1;
        }
        return tokens;
    }

    /**
     * 查找右括号.
     *
     * @param value 文本
     * @param openIndex 左括号下标
     * @return 右括号下标
     */
    private int findClosingParen(String value, int openIndex) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean escaped = false;
        for (int i = openIndex; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inSingleQuote) {
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Unclosed placeholder token starting at index " + (openIndex - 1));
    }

    /**
     * 判断是否是函数名起始字符.
     *
     * @param ch 字符
     * @return 是否是函数名起始字符
     */
    private boolean isFunctionNameStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    /**
     * 判断是否是函数名.
     *
     * @param name 函数名
     * @return 是否是函数名
     */
    private boolean isFunctionName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!isFunctionNameStart(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean valid = Character.isLetterOrDigit(ch) || ch == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}

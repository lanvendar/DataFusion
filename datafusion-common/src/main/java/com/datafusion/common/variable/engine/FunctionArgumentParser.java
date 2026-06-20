package com.datafusion.common.variable.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数参数解析器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class FunctionArgumentParser {

    /**
     * 解析参数列表.
     *
     * @param argumentsText 参数文本
     * @return 参数列表
     */
    public List<String> parse(String argumentsText) {
        List<String> arguments = new ArrayList<>();
        if (argumentsText == null || argumentsText.trim().isEmpty()) {
            return arguments;
        }

        StringBuilder current = new StringBuilder();
        boolean inDoubleQuote = false;
        boolean escaped = false;
        for (int i = 0; i < argumentsText.length(); i++) {
            char ch = argumentsText.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                current.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"') {
                current.append(ch);
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (ch == ',' && !inDoubleQuote) {
                arguments.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            arguments.add(tail);
        }
        return arguments;
    }
}

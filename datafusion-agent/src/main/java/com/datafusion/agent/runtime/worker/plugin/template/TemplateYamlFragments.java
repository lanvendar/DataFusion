package com.datafusion.agent.runtime.worker.plugin.template;

import java.util.List;
import java.util.Map;

/**
 * YAML fragment helper for execution spec templates.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/9
 * @since 1.0.0
 */
public final class TemplateYamlFragments {

    private TemplateYamlFragments() {
    }

    /**
     * Render list items.
     *
     * @param values list values
     * @param indent indentation
     * @return YAML fragment
     */
    public static String listItems(List<String> values, int indent) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            values.forEach(value -> builder.append(spaces(indent))
                    .append("- ").append(quote(value)).append(System.lineSeparator()));
        }
        return builder.toString();
    }

    /**
     * Render map entries.
     *
     * @param values map values
     * @param indent indentation
     * @return YAML fragment
     */
    public static String mapEntries(Map<String, String> values, int indent) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            values.forEach((key, value) -> builder.append(spaces(indent))
                    .append(quote(key)).append(": ").append(quote(value)).append(System.lineSeparator()));
        }
        return builder.toString();
    }

    /**
     * Quote YAML scalar.
     *
     * @param value value
     * @return quoted scalar
     */
    public static String quote(String value) {
        String text = value == null ? "" : value;
        String escaped = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return '"' + escaped + '"';
    }

    private static String spaces(int count) {
        return " ".repeat(Math.max(count, 0));
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datafusion.common.options.description;

import java.util.EnumSet;

/**
 * Allows providing multiple formatters for the description. E.g. Html formatter, Markdown formatter
 * etc.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public abstract class Formatter {
    /**
     * The state of the formatter.
     */
    private final StringBuilder state = new StringBuilder();
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param description description to be formatted
     * @return string representation of the description
     */
    public String format(Description description) {
        for (BlockElement blockElement : description.getBlocks()) {
            blockElement.format(this);
        }
        return finalizeFormatting();
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param element to be formatted
     */
    public void format(LinkElement element) {
        formatLink(state, element.getLink(), element.getText());
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param element element
     */
    public void format(TextElement element) {
        String[] inlineElements =
                element.getElements().stream()
                        .map(
                                el -> {
                                    Formatter formatter = newInstance();
                                    el.format(formatter);
                                    return formatter.finalizeFormatting();
                                })
                        .toArray(String[]::new);
        formatText(
                state,
                escapeFormatPlaceholder(element.getFormat()),
                inlineElements,
                element.getStyles());
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param element element
     */
    public void format(LineBreakElement element) {
        formatLineBreak(state);
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param element element
     */
    public void format(ListElement element) {
        String[] inlineElements =
                element.getEntries().stream()
                        .map(
                                el -> {
                                    Formatter formatter = newInstance();
                                    el.format(formatter);
                                    return formatter.finalizeFormatting();
                                })
                        .toArray(String[]::new);
        formatList(state, inlineElements);
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @return string representation of the description
     */
    private String finalizeFormatting() {
        String result = state.toString();
        state.setLength(0);
        return result.replaceAll("%%", "%");
    }
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param state       state
     * @param link        link
     * @param description description
     */
    protected abstract void formatLink(StringBuilder state, String link, String description);
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param state state
     */
    protected abstract void formatLineBreak(StringBuilder state);
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param state    state
     * @param format   format
     * @param elements elements
     * @param styles   styles
     */
    protected abstract void formatText(
            StringBuilder state,
            String format,
            String[] elements,
            EnumSet<TextElement.TextStyle> styles);
    
    /**
     * Formats the description into a String using format specific tags.
     *
     * @param state   state
     * @param entries entries
     */
    protected abstract void formatList(StringBuilder state, String[] entries);
    
    /**
     * Creates a new instance of the formatter.
     *
     * @return new instance of the formatter
     */
    protected abstract Formatter newInstance();
    
    /**
     * temporary placeholder for string format.
     */
    private static final String TEMPORARY_PLACEHOLDER = "randomPlaceholderForStringFormat";
    
    /**
     * Escape format placeholder.
     *
     * @param value value
     * @return escaped value
     */
    private static String escapeFormatPlaceholder(String value) {
        return value.replaceAll("%s", TEMPORARY_PLACEHOLDER)
                .replaceAll("%", "%%")
                .replaceAll(TEMPORARY_PLACEHOLDER, "%s");
    }
}

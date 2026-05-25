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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.datafusion.common.utils.Preconditions.checkArgument;
import static com.datafusion.common.utils.Preconditions.checkNotNull;

/**
 * Represents a text block in the {@link Description}.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class TextElement implements BlockElement, InlineElement {
    /**
     * Text with placeholders ("%s") that will be replaced with proper string representation of
     * given {@link InlineElement}.
     */
    private final String format;
    
    /**
     * Elements that will be put in the text.
     */
    private final List<InlineElement> elements;
    
    /**
     * Styles that can be applied to {@link TextElement} e.g. code, bold etc.
     */
    private final EnumSet<TextStyle> textStyles = EnumSet.noneOf(TextStyle.class);
    
    /**
     * Creates a block of text with placeholders ("%s") that will be replaced with proper string
     * representation of given {@link InlineElement}. For example:
     *
     * <p>{@code text("This is a text with a link %s", link("https://somepage", "to here"))}
     *
     * @param format   text with placeholders for elements
     * @param elements elements to be put in the text
     * @return block of text
     */
    public static TextElement text(String format, InlineElement... elements) {
        return new TextElement(format, Arrays.asList(elements));
    }
    
    /**
     * Creates a simple block of text.
     *
     * @param text a simple block of text
     * @return block of text
     */
    public static TextElement text(String text) {
        return new TextElement(text, Collections.emptyList());
    }
    
    /**
     * Wraps a list of {@link InlineElement}s into a single {@link TextElement}.
     *
     * @param elements elements to be put in the text
     * @return block of text
     */
    public static InlineElement wrap(InlineElement... elements) {
        return text(repeat("%s", elements.length), elements);
    }
    
    /**
     * Returns a string consisting of a specific number of concatenated copies of an input string.
     * For example, {@code repeat("hey", 3)} returns the string {@code "heyheyhey"}.
     *
     * @param string any non-null string
     * @param count  the number of times to repeat it; a nonnegative integer
     * @return a string containing {@code string} repeated {@code count} times (the empty string if
     * {@code count} is zero)
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static String repeat(String string, int count) {
        checkNotNull(string); // eager for GWT.
        
        if (count <= 1) {
            checkArgument(count >= 0, "invalid count: %s", count);
            return (count == 0) ? "" : string;
        }
        
        // IF YOU MODIFY THE CODE HERE, you must update StringsRepeatBenchmark
        final int len = string.length();
        final long longSize = (long) len * (long) count;
        final int size = (int) longSize;
        if (size != longSize) {
            throw new ArrayIndexOutOfBoundsException("Required array size too large: " + longSize);
        }
        
        final char[] array = new char[size];
        string.getChars(0, len, array, 0);
        int n;
        for (n = len; n < size - n; n <<= 1) {
            System.arraycopy(array, 0, array, n, n);
        }
        System.arraycopy(array, 0, array, n, size - n);
        return new String(array);
    }
    
    /**
     * Creates a block of text formatted as code.
     *
     * @param text a block of text that will be formatted as code
     * @return block of text formatted as code
     */
    public static TextElement code(String text) {
        TextElement element = text(text);
        element.textStyles.add(TextStyle.CODE);
        return element;
    }
    
    public String getFormat() {
        return format;
    }
    
    public List<InlineElement> getElements() {
        return elements;
    }
    
    public EnumSet<TextStyle> getStyles() {
        return textStyles;
    }
    
    /**
     * Creates a block of text with placeholders ("%s") that will be replaced with proper string
     * representation of given {@link InlineElement}. For example:
     *
     * <p>{@code text("This is a text with a link %s", link("https://somepage", "to here"))}
     *
     * @param format   text with placeholders for elements
     * @param elements elements to be put in the text
     */
    private TextElement(String format, List<InlineElement> elements) {
        this.format = format;
        this.elements = elements;
    }
    
    @Override
    public void format(Formatter formatter) {
        formatter.format(this);
    }
    
    /**
     * Styles that can be applied to {@link TextElement} e.g. code, bold etc.
     */
    public enum TextStyle {
        /**
         * Code.
         */
        CODE
    }
}

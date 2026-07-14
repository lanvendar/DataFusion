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

package com.datafusion.common.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.datafusion.common.utils.Preconditions.checkNotNull;

/**
 * Helper class for splitting a string on a given delimiter with quoting logic.
 *
 * @author DataFusion
 * @version 1.0.0
 */
class StructuredOptionsSplitter {
    
    /**
     * Splits the given string on the given delimiter. It supports quoting parts of the string with
     * either single (') or double quotes ("). Quotes can be escaped by doubling the quotes.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>'A;B';C => [A;B], [C]
     *   <li>"AB'D";B;C => [AB'D], [B], [C]
     *   <li>"AB'""D;B";C => [AB'\"D;B], [C]
     * </ul>
     *
     * <p>For more examples check the tests.
     *
     * @param string    a string to split
     * @param delimiter delimiter to split on
     * @return a list of splits
     */
    static List<String> splitEscaped(String string, char delimiter) {
        List<Token> tokens = tokenize(checkNotNull(string), delimiter);
        return processTokens(tokens);
    }
    
    /**
     * Escapes the given string with single quotes, if the input string contains a double quote or
     * any of the given {@code charsToEscape}. Any single quotes in the input string will be escaped
     * by doubling.
     *
     * <p>Given that the escapeChar is (;)
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>A,B,C,D => A,B,C,D
     *   <li>A'B'C'D => 'A''B''C''D'
     *   <li>A;BCD => 'A;BCD'
     *   <li>AB"C"D => 'AB"C"D'
     *   <li>AB'"D:B => 'AB''"D:B'
     * </ul>
     *
     * @param string        a string which needs to be escaped
     * @param charsToEscape escape chars for the escape conditions
     * @return escaped string by single quote
     */
    static String escapeWithSingleQuote(String string, String... charsToEscape) {
        boolean escape =
                Arrays.stream(charsToEscape).anyMatch(string::contains)
                        || string.contains("\"")
                        || string.contains("'");
        
        if (escape) {
            return "'" + string.replaceAll("'", "''") + "'";
        }
        
        return string;
    }
    
    /**
     * Processes the tokens to get the splits.
     *
     * @param tokens a list of tokens
     * @return a list of splits
     */
    private static List<String> processTokens(List<Token> tokens) {
        final List<String> splits = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            switch (token.getTokenType()) {
                case DOUBLE_QUOTED:
                case SINGLE_QUOTED:
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).getTokenType() != TokenType.DELIMITER) {
                        int illegalPosition = tokens.get(i + 1).getPosition() - 1;
                        throw new IllegalArgumentException(
                                "Could not split string. Illegal quoting at position: "
                                        + illegalPosition);
                    }
                    splits.add(token.getString());
                    break;
                case UNQUOTED:
                    splits.add(token.getString());
                    break;
                case DELIMITER:
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).getTokenType() == TokenType.DELIMITER) {
                        splits.add("");
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Could not split string. Illegal token type: " + token.getTokenType());
            }
        }
        
        return splits;
    }
    
    /**
     * Tokenizes the given string.
     *
     * @param string    a string to tokenize
     * @param delimiter delimiter to split on
     * @return a list of tokens
     */
    private static List<Token> tokenize(String string, char delimiter) {
        final List<Token> tokens = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();
        for (int cursor = 0; cursor < string.length(); ) {
            final char c = string.charAt(cursor);
            
            int nextChar = cursor + 1;
            if (c == '\'') {
                nextChar = consumeInQuotes(string, '\'', cursor, builder);
                tokens.add(new Token(TokenType.SINGLE_QUOTED, builder.toString(), cursor));
            } else if (c == '"') {
                nextChar = consumeInQuotes(string, '"', cursor, builder);
                tokens.add(new Token(TokenType.DOUBLE_QUOTED, builder.toString(), cursor));
            } else if (c == delimiter) {
                tokens.add(new Token(TokenType.DELIMITER, String.valueOf(c), cursor));
            } else if (!Character.isWhitespace(c)) {
                nextChar = consumeUnquoted(string, delimiter, cursor, builder);
                tokens.add(new Token(TokenType.UNQUOTED, builder.toString().trim(), cursor));
            }
            builder.setLength(0);
            cursor = nextChar;
        }
        
        return tokens;
    }
    
    /**
     * Consumes the string in quotes.
     *
     * @param string  a string to consume
     * @param quote   quote to consume
     * @param cursor  current cursor position
     * @param builder a string builder to append the consumed string
     * @return the next cursor position
     */
    private static int consumeInQuotes(
            String string, char quote, int cursor, StringBuilder builder) {
        for (int i = cursor + 1; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == quote) {
                if (i + 1 < string.length() && string.charAt(i + 1) == quote) {
                    builder.append(c);
                    i += 1;
                } else {
                    return i + 1;
                }
            } else {
                builder.append(c);
            }
        }
        
        throw new IllegalArgumentException(
                "Could not split string. Quoting was not closed properly.");
    }
    
    /**
     * Consumes the string until the delimiter is found.
     *
     * @param string    a string to consume
     * @param delimiter delimiter to consume until
     * @param cursor    current cursor position
     * @param builder   a string builder to append the consumed string
     * @return the next cursor position
     */
    private static int consumeUnquoted(
            String string, char delimiter, int cursor, StringBuilder builder) {
        int i;
        for (i = cursor; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == delimiter) {
                return i;
            }
            
            builder.append(c);
        }
        
        return i;
    }
    
    private enum TokenType {
        /**
         * 双引号.
         */
        DOUBLE_QUOTED,
        /**
         * 单引号.
         */
        SINGLE_QUOTED,
        /**
         * 无引号.
         */
        UNQUOTED,
        /**
         * 分割符.
         */
        DELIMITER
    }
    
    private static class Token {
        /**
         * 符号类型.
         */
        private final TokenType tokenType;
        
        /**
         * 字符串.
         */
        private final String string;
        
        /**
         * 字符串位置.
         */
        private final int position;
        
        /**
         * 构造方法.
         *
         * @param tokenType 符号类型
         * @param string    字符串
         * @param position  字符串位置
         */
        private Token(TokenType tokenType, String string, int position) {
            this.tokenType = tokenType;
            this.string = string;
            this.position = position;
        }
        
        public TokenType getTokenType() {
            return tokenType;
        }
        
        public String getString() {
            return string;
        }
        
        public int getPosition() {
            return position;
        }
    }
    
    /**
     * 私有构造方法.
     */
    private StructuredOptionsSplitter() {
    }
}

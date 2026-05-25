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

import java.util.ArrayList;
import java.util.List;

/**
 * Description for {@link com.datafusion.common.options.ConfigOption}. Allows providing multiple rich
 * formats.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class Description {
    /**
     * Part of description that represents a block e.g. some text, linebreak or a list.
     */
    private final List<BlockElement> blocks;
    
    /**
     * Creates a builder for {@link Description}.
     *
     * @return DescriptionBuilder
     */
    public static DescriptionBuilder builder() {
        return new DescriptionBuilder();
    }
    
    /**
     * Returns all blocks of description.
     *
     * @return blocks
     */
    public List<BlockElement> getBlocks() {
        return blocks;
    }
    
    /**
     * Builder for {@link Description}. Allows adding a rich formatting like lists, links,
     * linebreaks etc. For example:
     *
     * <pre>{@code
     * Description description = Description.builder()
     *  .text("This is some list: ")
     *  .list(text("this is first element of list"),text("this is second element of list with a %s", link("https://link")))
     *  .build();
     * }</pre>
     */
    public static class DescriptionBuilder {
        /**
         * Part of description that represents a block e.g. some text, linebreak or a list.
         */
        private final List<BlockElement> blocks = new ArrayList<>();
        
        /**
         * Adds a block of text with placeholders ("%s") that will be replaced with proper string
         * representation of given {@link InlineElement}. For example:
         *
         * <p>{@code text("This is a text with a link %s", link("https://somepage", "to here"))}
         *
         * @param format   text with placeholders for elements
         * @param elements elements to be put in the text
         * @return description with added block of text
         */
        public DescriptionBuilder text(String format, InlineElement... elements) {
            blocks.add(TextElement.text(format, elements));
            return this;
        }
        
        /**
         * Creates a simple block of text.
         *
         * @param text a simple block of text
         * @return block of text
         */
        public DescriptionBuilder text(String text) {
            blocks.add(TextElement.text(text));
            return this;
        }
        
        /**
         * Block of description add.
         *
         * @param block block of description to add
         * @return block of description
         */
        public DescriptionBuilder add(BlockElement block) {
            blocks.add(block);
            return this;
        }
        
        /**
         * Creates a line break in the description.
         *
         * @return DescriptionBuilder
         */
        public DescriptionBuilder linebreak() {
            blocks.add(LineBreakElement.linebreak());
            return this;
        }
        
        /**
         * Adds a bulleted list to the description.
         *
         * @param elements elements of the list
         * @return DescriptionBuilder
         */
        public DescriptionBuilder list(InlineElement... elements) {
            blocks.add(ListElement.list(elements));
            return this;
        }
        
        /**
         * Creates description representation.
         *
         * @return Description
         */
        public Description build() {
            return new Description(blocks);
        }
    }
    
    /**
     * Creates description representation.
     */
    private Description(List<BlockElement> blocks) {
        this.blocks = blocks;
    }
}

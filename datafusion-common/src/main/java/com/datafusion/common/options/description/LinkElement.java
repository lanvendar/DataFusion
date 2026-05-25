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

/**
 * Element that represents a link in the {@link Description}.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class LinkElement implements InlineElement {
    /**
     * The link address.
     */
    private final String link;
    
    /**
     * The link description.
     */
    private final String text;
    
    /**
     * Creates a link with a given url and description.
     *
     * @param link address that this link should point to
     * @param text a description for that link, that should be used in text
     * @return link representation
     */
    public static LinkElement link(String link, String text) {
        return new LinkElement(link, text);
    }
    
    /**
     * Creates a link with a given url. This url will be used as a description for that link.
     *
     * @param link address that this link should point to
     * @return link representation
     */
    public static LinkElement link(String link) {
        return new LinkElement(link, link);
    }
    
    public String getLink() {
        return link;
    }
    
    public String getText() {
        return text;
    }
    
    /**
     * Creates a link with a given url and description.
     *
     * @param link address that this link should point to
     * @param text a description for that link, that should be used in text
     */
    private LinkElement(String link, String text) {
        this.link = link;
        this.text = text;
    }
    
    @Override
    public void format(Formatter formatter) {
        formatter.format(this);
    }
}

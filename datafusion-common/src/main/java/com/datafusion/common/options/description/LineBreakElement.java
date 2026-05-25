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
 * Represents a line break in the {@link Description}.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class LineBreakElement implements InlineElement, BlockElement {
    
    /**
     * Creates a line break in the description.
     *
     * @return LineBreakElement
     */
    public static LineBreakElement linebreak() {
        return new LineBreakElement();
    }
    
    /**
     * 构造函数，私有，不允许外部实例化.
     */
    private LineBreakElement() {
    }
    
    @Override
    public void format(Formatter formatter) {
        formatter.format(this);
    }
}

package com.datafusion.common.enums;

import com.datafusion.common.template.ext.directive.PlaceHolderDirective;

import java.util.EnumSet;

/**
 * sql模板渲染类型.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/6/23
 * @since 2025/6/23
 */
public enum RenderType {
    /**
     * 原样输出.
     */
    ORIGINAL("original"),
    /**
     * 普通渲染.
     */
    NORMAL("normal"),
    /**
     * 占位符渲染{@link PlaceHolderDirective}.
     */
    SYMBOL("symbol");
    
    /**
     * sql的渲染类型.
     */
    private final String value;
    
    /**
     * 构造函数.
     *
     * @param value sql的渲染类型
     */
    RenderType(String value) {
        this.value = value;
    }
    
    @Override
    public String toString() {
        return this.value;
    }
    
    /**
     * 根据value获取对应的枚举.
     *
     * @param value sql的渲染类型
     * @return sql的渲染类型枚举
     */
    public static RenderType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return EnumSet.allOf(RenderType.class).stream().filter(s -> s.toString().equals(value)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid RenderType: " + value));
    }
}

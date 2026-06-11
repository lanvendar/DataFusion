package com.datafusion.scheduler.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 变量类型枚举.
 *
 * @author lanvendar
 * @version 3.0 2022/4/28
 * @since 2022/4/28
 */
public enum VarType {
    /**
     * 输入参数.
     */
    @JsonProperty("in") IN,
    /**
     * 输出参数.
     */
    @JsonProperty("out") OUT;
}

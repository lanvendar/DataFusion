package com.datafusion.manager.asset.enums;

import lombok.Getter;

/**
 * 资源状态枚举类.
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/19
 * @since 2025/11/19
 */
@Getter
public enum ResourceStatusEnum {

    /**
     * 导入资源成功.
     */
    IMPORT_SUCCESS(0, "导入完成"),
    /**
     * 导入资源失败.
     */
    IMPORT_FAILED(1, "导入失败"),
    /**
     * 解析中.
     */
    PARSE_ING(2, "解析中"),
    /**
     * 解析成功.
     */
    PARSE_SUCCESS(3, "解析成功"),
    /**
     * 解析失败.
     */
    PARSE_FAILED(4, "解析失败"),
    /**
     * 录入血缘成功.
     */
    IMPORT_EDGE_SUCCESS(5, "录入血缘成功"),
    /**
     * 录入血缘失败.
     */
    IMPORT_EDGE_FAILED(6, "录入血缘失败");
    
    /**
     * 状态.
     */
    Integer status;
    
    /**
     * 状态描述.
     */
    String statusDesc;
    
    /**
     * 构造函数.
     */
    ResourceStatusEnum(int status, String statusDesc) {
        this.status = status;
        this.statusDesc = statusDesc;
    }
    
}

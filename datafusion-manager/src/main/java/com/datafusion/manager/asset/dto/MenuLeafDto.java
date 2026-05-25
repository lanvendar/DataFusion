package com.datafusion.manager.asset.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/13
 * @since 2025/11/13
 */
@Data
@Accessors(chain = true)
public class MenuLeafDto {
    
    /**
     * 叶子permission id.
     */
    private Integer id;
    
    /**
     * 叶子permission 名称.
     */
    private String name;
    
    /**
     * 叶子permission code.
     */
    private String code;
    
    /**
     *  appid.
     */
    private Integer appId;
    
    /**
     *  appCode.
     */
    private String appCode;
    
    /**
     *  菜单名称全路径.
     */
    private String fullNamePath;
    
    /**
     *  菜单code全路径.
     */
    private String fullCodePath;
    
}

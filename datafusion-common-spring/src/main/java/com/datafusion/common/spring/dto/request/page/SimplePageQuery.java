package com.datafusion.common.spring.dto.request.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 纯分页请求对象.
 *
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
@Data
public class SimplePageQuery {
    
    /**
     * 构造函数.
     */
    public SimplePageQuery() {
    }
    
    /**
     * 构造函数.
     *
     * @param size    分页每页条数
     * @param current 页码
     */
    public SimplePageQuery(Integer size, Integer current) {
        if (size != null) {
            this.size = size;
        }
        if (current != null) {
            this.current = current;
        }
    }
    
    /**
     * 每页多少条.
     */
    @Schema(name = "size", description = "分页默认10条")
    private Integer size = 10;
    
    /**
     * 页码.
     */
    @Schema(name = "current", description = "当前页数（从1开始）")
    private Integer current = 1;
    
    /**
     * 计算分页时的offset.
     */
    @Schema(name = "offset", description = "分页时的offset")
    private int offset = (getOffset() - 1) * getSize();
}

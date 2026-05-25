package com.datafusion.common.spring.dto.request.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排序属性.
 *
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
@Data
@Schema(name = "Order", description = "排序属性")
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    /**
     * 排序字段.
     */
    @Schema(name = "property", description = "排序字段名")
    private String property;

    /**
     * 升序还是降序.
     */
    @Schema(name = "asc", description = "升序/降序")
    private Boolean asc;

}

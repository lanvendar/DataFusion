package com.datafusion.common.spring.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本查询对象只是一层封装,也可以使用纯 pojo 对象.
 *
 * @param <T> 查询对象
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query<T> {
    /**
     * 查询对象.
     */
    @Schema(name = "option", description = "查询对象")
    private T option;
}

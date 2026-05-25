package com.datafusion.common.spring.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 限制条数的查询.
 *
 * @param <T> 查询对象
 * @author lanvendar
 * @version 1.0.0, 2025/8/28
 * @since 2025/8/28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class QueryLimit<T> extends Query<T> {
    
    /**
     * 限制条数.
     */
    @Schema(name = "limit", description = "限制条数")
    private Integer limit;
}
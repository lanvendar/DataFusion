package com.datafusion.manager.asset.dto.skywalking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * MetricsTagDto.
 * @author xufeng
 * @version 1.0.0, 2026/4/13
 * @since 2026/4/13
 */
@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class MetricsTagDto {

    /**
     * 测点.
     */
    private String tag;

    /**
     * 维度.
     */
    private String dimension;
}

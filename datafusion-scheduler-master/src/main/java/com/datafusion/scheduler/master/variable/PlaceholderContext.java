package com.datafusion.scheduler.master.variable;

import com.datafusion.common.variable.VariableRenderContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 占位符处理上下文.
 * 包含处理占位符所需的所有参数.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PlaceholderContext extends VariableRenderContext {

    /**
     * 调度时间戳.
     */
    private Long scheduleTime;

    @Override
    public Long getDefaultTimeMillis() {
        return scheduleTime;
    }
}

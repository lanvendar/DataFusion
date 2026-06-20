package com.datafusion.scheduler.master.param;

import com.datafusion.scheduler.model.Variable;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 占位符处理上下文.
 * 包含处理占位符所需的所有参数.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Data
@Builder
public class PlaceholderContext {

    /**
     * 调度时间戳.
     */
    private Long scheduleTime;

    /**
     * 变量映射.
     * key: 变量名, value: Variable对象
     */
    private Map<String, Variable> variables;
}

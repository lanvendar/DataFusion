package com.datafusion.common.variable.builtin;

import com.datafusion.scheduler.model.Variable;

import java.util.Map;

/**
 * 变量渲染上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class VariableRenderContext {

    /**
     * 调度时间戳.
     */
    private Long scheduleTime;

    /**
     * 变量映射.
     */
    private Map<String, Variable> variables;

    /**
     * 创建 builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取调度时间戳.
     *
     * @return 调度时间戳
     */
    public Long getScheduleTime() {
        return scheduleTime;
    }

    /**
     * 设置调度时间戳.
     *
     * @param scheduleTime 调度时间戳
     */
    public void setScheduleTime(Long scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    /**
     * 获取变量映射.
     *
     * @return 变量映射
     */
    public Map<String, Variable> getVariables() {
        return variables;
    }

    /**
     * 设置变量映射.
     *
     * @param variables 变量映射
     */
    public void setVariables(Map<String, Variable> variables) {
        this.variables = variables;
    }

    /**
     * builder.
     */
    public static class Builder {

        /**
         * 上下文对象.
         */
        private final VariableRenderContext context = new VariableRenderContext();

        /**
         * 设置调度时间戳.
         *
         * @param scheduleTime 调度时间戳
         * @return builder
         */
        public Builder scheduleTime(Long scheduleTime) {
            context.setScheduleTime(scheduleTime);
            return this;
        }

        /**
         * 设置变量映射.
         *
         * @param variables 变量映射
         * @return builder
         */
        public Builder variables(Map<String, Variable> variables) {
            context.setVariables(variables);
            return this;
        }

        /**
         * 构建上下文.
         *
         * @return 上下文
         */
        public VariableRenderContext build() {
            return context;
        }
    }
}

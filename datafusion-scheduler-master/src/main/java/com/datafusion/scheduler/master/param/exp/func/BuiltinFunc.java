package com.datafusion.scheduler.master.param.exp.func;

/**
 * 内置函数.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
public interface BuiltinFunc {

    /**
     * 函数名称.
     *
     * @return 函数名称
     */
    String name();

    /**
     * 调用方法.
     *
     * @param scheduleTime 调度时间
     * @param align       对齐格式（用于 biz_date）
     * @param args        参数
     * @return 结果
     */
    String call(Long scheduleTime, String align, String... args);
}

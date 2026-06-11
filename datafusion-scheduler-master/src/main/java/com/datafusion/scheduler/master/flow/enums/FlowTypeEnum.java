package com.datafusion.scheduler.master.flow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

/**
 * 任务类型枚举.
 *
 * @author 李正凯
 * @version 3.0 2022/5/6
 * @since 2022/5/6
 */
@AllArgsConstructor
public enum FlowTypeEnum {
    /**
     * stream流任务.
     */
    STREAM("1"),
    /**
     * batch批任务.
     */
    BATCH("2");
    
    /**
     * type code.
     */
    @Getter
    String type;
    
    /**
     * 字符串转化枚举类型.
     *
     * @param target 目标字符串
     * @return 返回枚举
     */
    public static FlowTypeEnum fromString(String target) {
        if (target == null) {
            return null;
        }
        return EnumSet.allOf(FlowTypeEnum.class).stream().filter(s -> s.getType().equals(target)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid StateEnum: " + target));
    }
    
    /**
     * 判断是否为批类型.
     *
     * @return true:批类型 false:流类型
     */
    public boolean isBatch() {
        return this == BATCH;
    }
}

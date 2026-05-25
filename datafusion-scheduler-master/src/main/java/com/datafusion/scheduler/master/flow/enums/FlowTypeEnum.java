/*
 * Copyright © 2020-2022 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

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

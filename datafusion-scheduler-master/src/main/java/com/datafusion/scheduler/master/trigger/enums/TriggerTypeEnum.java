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

package com.datafusion.scheduler.master.trigger.enums;

/**
 * 调度类型枚举.
 *
 * @author lanvendar
 * @version 3.0 2022/5/6
 * @since 2022/5/6
 */
public enum TriggerTypeEnum {
    /**
     * cron表达式.
     */
    CRON,
    /**
     * 时间间隔类型:单位/毫秒.
     */
    INTERVAL;

    /**
     * 根据int值转换成枚举.
     *
     * @param i int值
     * @return 枚举值
     */
    public static TriggerTypeEnum valueOf(int i) {
        TriggerTypeEnum[] policies = TriggerTypeEnum.values();
        if (i > policies.length - 1 || i < 0) {
            return INTERVAL;
        } else {
            return policies[i];
        }
    }
}

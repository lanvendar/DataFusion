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
 * 调度策略枚举.
 *
 * @author lanvendar
 * @version 3.0 2022/5/6
 * @since 2022/5/6
 */
public enum TriggerPolicyEnum {

    /**
     * 执行一次.
     */
    EXECUTE_ONCE,
    /**
     * 顺序执行.
     */
    SERIAL_WAIT,
    /**
     * 重复执行.
     */
    PARALLEL,
    /**
     * 丢弃最新.
     */
    DISCARD_NEW,
    /**
     * 覆盖执行.
     */
    DISCARD_OLD;

    /**
     * 根据int值转换成枚举.
     *
     * @param i int值
     * @return 枚举值
     */
    public static TriggerPolicyEnum valueOf(int i) {
        TriggerPolicyEnum[] policies = TriggerPolicyEnum.values();
        if (i > policies.length - 1 || i < 0) {
            return EXECUTE_ONCE;
        } else {
            return policies[i];
        }
    }
}

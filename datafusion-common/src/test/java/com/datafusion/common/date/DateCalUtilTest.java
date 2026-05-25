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

package com.datafusion.common.date;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Date;

/**
 * 测试日期计算类.
 *
 * @author 李正凯
 * @version 3.0 2022/6/22
 * @since 2022/6/22
 */
@Slf4j
public class DateCalUtilTest {
    /**
     * 测试根据日期结尾参数，如WS、WD、MS、MD、SS、SD、YS、YD来计算日期.
     */
    @Test
    public void calDateBySuffixExp() {
        log.info("{}", DateUtil.beginOfQuarter(new Date()));
        log.info("{}", DateUtil.endOfQuarter(new Date()));
        log.info("{}", DateUtil.beginOfWeek(new Date()));
        log.info("{}", DateUtil.endOfWeek(new Date()));
        log.info("{}", DateUtil.beginOfMonth(new Date()));
        log.info("{}", DateUtil.endOfMonth(new Date()));
        log.info("{}", DateUtil.beginOfYear(new Date()));
        log.info("{}", DateUtil.endOfYear(new Date()));
        log.info("{}", DateUtil.parse("2022-06-23", "yyyy-MM-dd"));
    }
}
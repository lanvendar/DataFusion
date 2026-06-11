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
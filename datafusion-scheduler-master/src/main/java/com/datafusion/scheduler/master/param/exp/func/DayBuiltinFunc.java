package com.datafusion.scheduler.master.param.exp.func;

import com.datafusion.common.date.DateCalUtil;
import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * DAY函数处理.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
@AutoService(BuiltinFunc.class)
public class DayBuiltinFunc extends AbstractDateBuiltinFunc implements BuiltinFunc {

    @Override
    public String name() {
        return "DAY";
    }

    @Override
    protected String getResult(Date date, String pattern) {
        return DateCalUtil.format(date, pattern);
    }
}

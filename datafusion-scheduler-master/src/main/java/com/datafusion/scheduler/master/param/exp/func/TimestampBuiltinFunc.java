package com.datafusion.scheduler.master.param.exp.func;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 计算时间戳的函数.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/22
 * @since 2022/6/22
 */
@Slf4j
@AutoService(BuiltinFunc.class)
public class TimestampBuiltinFunc extends AbstractDateBuiltinFunc implements BuiltinFunc {

    @Override
    public String name() {
        return "TIMESTAMP";
    }

    @Override
    protected String getResult(Date date, String pattern) {
        return String.valueOf(date.getTime());
    }
}

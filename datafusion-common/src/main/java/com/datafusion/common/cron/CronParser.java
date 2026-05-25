package com.datafusion.common.cron;

import java.util.Date;
import java.util.List;

/**
 * CRON表达式解析.
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/18
 * @since 2018/11/18
 */
public interface CronParser {
    
    /**
     * 截止时间之前的运行时间.
     *
     * @param endDate 最终时间
     * @return 触发时间集合
     */
    List<Date> nextHasEndTime(Date endDate);
    
    /**
     * 固定次数的运行时间.
     *
     * @param num 触发次数
     * @return 触发时间集合
     */
    List<Date> nextHasNum(int num);
    
    /**
     * 当前时刻的下一个时刻 带缓存.
     *
     * @return 下一个执行时刻
     */
    Date nextCache();
    
    /**
     * 当前时刻的下一个时刻.
     *
     * @return 下一个执行时刻
     */
    Date next();
    
    /**
     * 某个时刻的下一个时刻.
     *
     * @param date 给定时刻
     * @return 下一个执行时刻
     */
    Date next(Date date);
    
    /**
     * 计算一天中的哪些时刻[时分秒]执行.
     *
     * @param date 给定日期
     * @return 哪些时刻[时分秒]
     */
    List<TimeOfDay> timesOfDay(Date date);
}


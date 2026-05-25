package com.datafusion.common.cron;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Cron 表达式解析器.
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/24
 * @since 2018/11/24
 */
public class CronParserBase implements CronParser {
    
    /**
     * Cron 表达式.
     */
    private String expression;
    
    /**
     * 时区.
     */
    private TimeZone timeZone;
    
    /**
     * next缓存最后一次时间.
     */
    private Date lastTime = new Date();
    
    /**
     * 默认预测10次.
     */
    private int nextDefault = 10;
    
    /**
     * 构造方法.
     *
     * @param expression cron 表达式
     */
    public CronParserBase(String expression) {
        this(expression, TimeZone.getDefault());
    }
    
    /**
     * 带时区构造方法.
     *
     * @param expression cron 表达式
     * @param timeZone   时区
     */
    public CronParserBase(String expression, TimeZone timeZone) {
        this.expression = expression;
        this.timeZone = timeZone;
    }
    
    @Override
    public List<Date> nextHasEndTime(Date endDate) {
        List<Date> dates = new ArrayList<>();
        Date lastTmp = new Date();
        while (true) {
            if (lastTmp.compareTo(endDate) == 1) {
                if (dates.size() > 0) {
                    dates.remove(dates.size() - 1);
                }
                break;
            }
            dates.add(CronUtil.next(expression, lastTmp));
            lastTmp = CronUtil.next(expression, lastTmp);
        }
        return dates;
    }
    
    @Override
    public List<Date> nextHasNum(int num) {
        List<Date> dates = new ArrayList<>();
        Date lastTmp = new Date();
        for (int i = 0; i < num; i++) {
            dates.add(CronUtil.next(expression, lastTmp));
            lastTmp = CronUtil.next(expression, lastTmp);
        }
        return dates;
    }
    
    @Override
    public Date nextCache() {
        lastTime = next(lastTime);
        return lastTime;
    }
    
    @Override
    public Date next() {
        return CronUtil.next(expression, new Date());
    }
    
    /**
     * 说明.
     *
     * <p>
     * 1、找到所有时分秒的组合并按照时分秒排序
     * 2、给定的时分秒在以上集合之前、之后处理
     * 3、给定时时分秒在以上集合中找到一个最小的位置
     * 4、day+1循环直到找到满足月、星期的那一天
     * 5、或者在列表中找到最小的即可
     *
     * @param date 给定时刻
     * @return 返回给定时刻的下一时刻
     */
    @Override
    public Date next(Date date) {
        return CronUtil.next(expression, date);
    }
    
    /**
     * 思路：1、切割cron表达式 2、转换每个域 3、计算执行时间点（关键算法，解析cron表达式） 4、计算某一天的哪些时间点执行.
     *
     * @param date 给定日期
     * @return 返回一天的哪些时间点执行集合
     */
    @Override
    public List<TimeOfDay> timesOfDay(Date date) {
        return CronUtil.timesOfDay(expression, date);
    }
}

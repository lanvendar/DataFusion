package com.datafusion.common.cron;

/**
 * cron表达式某个位置上的一些常量，跟cron表达式的域一一对应. { 顺序        0       1      2   3     4     5        6 cron       0      15     10 ?
 * *   MON-FRI  (2018) cron域   SECOND、MINUTE、HOUR、DAY、MONTH、WEEK    (YEAR) }
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/17
 * @since 2018/11/17
 */
public enum CronPosition {
    /**
     * 秒.
     */
    SECOND(0, 59),
    /**
     * 分.
     */
    MINUTE(0, 59),
    /**
     * 小时.
     */
    HOUR(0, 23),
    /**
     * 天.
     */
    DAY(1, 31),
    /**
     * 月.
     */
    MONTH(1, 12),
    /**
     * 周.
     */
    WEEK(0, 6),
    /**
     * 年.
     */
    YEAR(2018, 2099);
    
    /**
     * 该域最小值.
     */
    private int min;
    
    /**
     * 该域最大值.
     */
    private int max;
    
    /**
     * 构造方法.
     *
     * @param min 该域最小值
     * @param max 该域最大值
     */
    CronPosition(int min, int max) {
        this.min = min;
        this.max = max;
    }
    
    public int getMin() {
        return min;
    }
    
    public int getMax() {
        return max;
    }
    
    /**
     * 取出值域和值范围枚举.
     *
     * @param position 值域位置
     * @return 返回值域和值范围
     */
    public static CronPosition fromPosition(int position) {
        for (CronPosition cronPosition : CronPosition.values()) {
            if (position == cronPosition.ordinal()) {
                return cronPosition;
            }
        }
        return null;
    }
}


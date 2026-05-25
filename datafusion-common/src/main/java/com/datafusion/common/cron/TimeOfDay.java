package com.datafusion.common.cron;

/**
 * 保存时分秒.
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/18
 * @since 2018/11/18
 */
public final class TimeOfDay implements Comparable<TimeOfDay> {
    
    /**
     * 小时.
     */
    private int hour;
    
    /**
     * 分.
     */
    private int minute;
    
    /**
     * 秒.
     */
    private int second;
    
    /**
     * 构造方法.
     *
     * @param hour   小时
     * @param minute 分
     * @param second 秒
     */
    public TimeOfDay(int hour, int minute, int second) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }
    
    public int getHour() {
        return hour;
    }
    
    public int getMinute() {
        return minute;
    }
    
    public int getSecond() {
        return second;
    }
    
    @Override
    public String toString() {
        return "TimeOfDay{" + "hour=" + hour + ", minute=" + minute + ", second=" + second + '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        TimeOfDay ofDay = (TimeOfDay) o;
        
        if (hour != ofDay.hour) {
            return false;
        }
        if (minute != ofDay.minute) {
            return false;
        }
        return second == ofDay.second;
    }
    
    @Override
    public int hashCode() {
        int result = hour;
        result = 31 * result + minute;
        result = 31 * result + second;
        return result;
    }
    
    /**
     * 按照时分秒的顺序逐个比较.
     */
    @Override
    public int compareTo(TimeOfDay o) {
        if (getHour() > o.getHour()) {
            return 1;
        }
        if (getHour() < o.getHour()) {
            return -1;
        }
        if (getMinute() > o.getMinute()) {
            return 1;
        }
        if (getMinute() < o.getMinute()) {
            return -1;
        }
        if (getSecond() > o.getSecond()) {
            return 1;
        }
        if (getSecond() < o.getSecond()) {
            return -1;
        }
        return 0;
    }
    
    /**
     * 计算两个时分秒时间的差距是否在给定的容忍范围内.
     *
     * @param another 比较的另外一方
     * @param seconds 容忍范围为秒
     * @return true if in the range of seconds or false if out of the range
     */
    public boolean equalsWithTolerance(TimeOfDay another, int seconds) {
        return DateUtil.equalsWithTolerance(this, another, seconds);
    }
}

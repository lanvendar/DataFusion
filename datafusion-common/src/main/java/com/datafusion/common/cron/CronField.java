package com.datafusion.common.cron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * cron表达式的域.
 *
 * @author lanvendar
 * @version 1.0.0 ,2018/11/18
 * @since 2018/11/18
 */
public class CronField {
    
    /**
     * cron表达式定义符号 "*" .
     */
    public static final String STAR = "*";
    
    /**
     * cron表达式定义符号 "," .
     */
    public static final String COMMA = ",";
    
    /**
     * cron表达式定义符号 "-" .
     */
    public static final String HYPHEN = "-";
    
    /**
     * cron表达式定义符号 "/" .
     */
    public static final String SLASH = "/";
    
    /**
     * cron表达式位置映射对象.
     */
    private CronPosition cronPosition;
    
    /**
     * cron表达式.
     */
    private String express;
    
    /**
     * cron表达式位置索引.
     */
    private List<Integer> listCache = null;
    
    /**
     * 构造方法.
     *
     * @param cronPosition cron表达式位置映射对象
     * @param express      cron表达式
     */
    public CronField(CronPosition cronPosition, String express) {
        this.cronPosition = cronPosition;
        this.express = express;
    }
    
    public CronPosition getCronPosition() {
        return cronPosition;
    }
    
    public String getExpress() {
        return express;
    }
    
    /**
     * 是否包含全部的数值，即是 *.
     *
     * @return boolean
     */
    public boolean containsAll() {
        return STAR.equals(express);
    }
    
    /**
     * 是否包含 ,.
     *
     * @return boolean
     */
    public boolean containsComma() {
        return express.contains(COMMA);
    }
    
    /**
     * 是否包含 -.
     *
     * @return boolean
     */
    public boolean containsHyphen() {
        return express.contains(HYPHEN);
    }
    
    /**
     * 是否包含 /.
     *
     * @return boolean
     */
    public boolean containsSlash() {
        return express.contains(SLASH);
    }
    
    /**
     * 3.计算某域的哪些点.
     *
     * @return 返回某域的值
     */
    public List<Integer> points() {
        //缓存计算的
        if (null != listCache) {
            return listCache;
        }
        
        listCache = new ArrayList<>(5);
        
        int min = cronPosition.getMin();
        int max = cronPosition.getMax();
        
        // *这种情况
        if (STAR.equals(express)) {
            for (int i = min; i <= max; i++) {
                listCache.add(i);
            }
            return listCache;
        }
        // 带有,的情况,分割之后每部分单独处理
        if (containsComma()) {
            String[] split = express.split(COMMA);
            for (String part : split) {
                listCache.addAll(new CronField(cronPosition, part).points());
            }
            if (listCache.size() > 1) {
                //去重
                CompareUtil.removeDuplicate(listCache);
                //排序
                Collections.sort(listCache);
            }
            
            return listCache;
        }
        // 0-3 0/2 3-15/2 5  模式统一为 (min-max)/step
        int left;
        int right;
        int step = 1;
        
        //包含-的情况
        if (containsHyphen()) {
            String[] strings = express.split(HYPHEN);
            left = Integer.parseInt(strings[0]);
            CompareUtil.assertRange(cronPosition, left);
            //1-32/2的情况
            if (strings[1].contains(SLASH)) {
                String[] split = strings[1].split(SLASH);
                //32
                right = Integer.parseInt(split[0]);
                CompareUtil.assertSize(left, right);
                CompareUtil.assertRange(cronPosition, right);
                //2
                step = Integer.parseInt(split[1]);
            } else {
                //1-32的情况
                right = Integer.parseInt(strings[1]);
                CompareUtil.assertSize(left, right);
                CompareUtil.assertRange(cronPosition, right);
            }
            //仅仅包含/
        } else if (containsSlash()) {
            String[] strings = express.split(SLASH);
            left = Integer.parseInt(strings[0]);
            CompareUtil.assertRange(cronPosition, left);
            step = Integer.parseInt(strings[1]);
            right = max;
            CompareUtil.assertSize(left, right);
        } else {
            // 普通的数字
            int single = Integer.parseInt(express);
            //星期域上 7 转换为 0
            if (CronPosition.WEEK == cronPosition && 7 == single) {
                single = 0;
            }
            CompareUtil.assertRange(cronPosition, single);
            listCache.add(single);
            return listCache;
        }
        
        for (int i = left; i <= right; i += step) {
            listCache.add(i);
        }
        return listCache;
        
    }
    
    @Override
    public String toString() {
        return "CronField{" + "cronPosition=" + cronPosition + ", express='" + express + '\'' + '}';
    }
}

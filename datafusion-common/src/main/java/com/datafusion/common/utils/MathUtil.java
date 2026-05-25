package com.datafusion.common.utils;

import java.text.DecimalFormat;

/**
 * 数学工具类.
 *
 * @author xf
 * @version 3.5.2, 2024/4/18
 * @since 1.0.0, 2020/5/19
 */
public class MathUtil {

    /**
     * fileSizeConverter.
     * @param sizeInBytes sizeInBytes
     * @return String
     */
    public static String fileSizeConverter(long sizeInBytes) {
        // 如果大小为 0，直接返回 "0 B"
        if (sizeInBytes <= 0) {
            return "0 B";
        }

        // 定义单位数组，从 B 开始
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB", "PB", "EB"};

        // 计算需要转换的次数
        // Math.log(sizeInBytes) / Math.log(1024) 的结果是 sizeInBytes 是 1024 的几次幂
        // (int) 强制取整，得到的就是单位数组的索引
        int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));

        // 使用 DecimalFormat 来格式化数字，保留最多两位小数
        // "#.##" 表示最多保留两位小数，如果小数部分是0，则不显示
        DecimalFormat df = new DecimalFormat("#,##0.##");

        // 计算最终的数值
        double finalSize = sizeInBytes / Math.pow(1024, digitGroups);

        // 拼接最终的字符串
        return df.format(finalSize) + " " + units[digitGroups];
    }
}

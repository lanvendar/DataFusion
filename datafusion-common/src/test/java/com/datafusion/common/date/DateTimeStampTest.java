package com.datafusion.common.date;

import com.datafusion.common.enums.TimeAlignmentEnum;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateTimeStamp单元测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/14
 * @since 2021/09/18
 */
public class DateTimeStampTest {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 测试getTime - 按天取整.
     */
    @Test
    public void testGetTimeByDay() {
        // 2023-06-15 14:30:45 -> 2023-06-15 00:00:00
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.DAY_1.getCode());

        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 00:00:00", resultStr);
    }

    /**
     * 测试getTime - 按小时取整.
     */
    @Test
    public void testGetTimeByHour() {
        // 2023-06-15 14:30:45 -> 2023-06-15 14:00:00
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.HOUR_1.getCode());

        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:00:00", resultStr);
    }

    /**
     * 测试getTime - 按月取整.
     */
    @Test
    public void testGetTimeByMonth() {
        // 2023-06-15 14:30:45 -> 2023-06-01 00:00:00
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_1.getCode());

        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-01 00:00:00", resultStr);
    }

    /**
     * 测试getTime - 按年取整.
     */
    @Test
    public void testGetTimeByYear() {
        // 2023-06-15 14:30:45 -> 2023-01-01 00:00:00
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.YEAR_1.getCode());

        String resultStr = formatTimestamp(result);
        assertEquals("2023-01-01 00:00:00", resultStr);
    }

    /**
     * 测试getTime - 按季度取整.
     */
    @Test
    public void testGetTimeByQuarter() {
        // 2023-06-15 -> Q2 start -> 2023-04-01
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_3.getCode());

        String resultStr = formatTimestamp(result);
        assertEquals("2023-04-01 00:00:00", resultStr);

        // 2023-08-15 -> Q3 start -> 2023-07-01
        timestamp = parseTimestamp("2023-08-15 14:30:45");
        result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_3.getCode());
        resultStr = formatTimestamp(result);
        assertEquals("2023-07-01 00:00:00", resultStr);
    }

    /**
     * 测试getNextTimeNature - 获取下一个周期.
     */
    @Test
    public void testGetNextTimeNature() {
        assertEquals(TimeAlignmentEnum.DAY_1_NEXT.getCode(),
                DateTimeStamp.getNextTimeNature(TimeAlignmentEnum.DAY_1.getCode()));
        assertEquals(TimeAlignmentEnum.HOUR_1_NEXT.getCode(),
                DateTimeStamp.getNextTimeNature(TimeAlignmentEnum.HOUR_1.getCode()));
        assertEquals(TimeAlignmentEnum.MONTH_1_NEXT.getCode(),
                DateTimeStamp.getNextTimeNature(TimeAlignmentEnum.MONTH_1.getCode()));
    }

    /**
     * 测试getAdd8TimeNature - 获取+8小时偏移的时间维度.
     */
    @Test
    public void testGetAdd8TimeNature() {
        assertEquals(TimeAlignmentEnum.DAY_1_ADD_8.getCode(),
                DateTimeStamp.getAdd8TimeNature(TimeAlignmentEnum.DAY_1.getCode()));
        assertEquals(TimeAlignmentEnum.HOUR_1_ADD_8.getCode(),
                DateTimeStamp.getAdd8TimeNature(TimeAlignmentEnum.HOUR_1.getCode()));
    }

    /**
     * 测试getTimeFromNature - 带+8小时偏移的时间取整.
     */
    @Test
    public void testGetTimeFromNatureWithAdd8() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTimeFromNature(timestamp, TimeAlignmentEnum.DAY_1_ADD_8.getCode());

        // 结果应该包含+8小时偏移
        assertNotNull(result);
    }

    /**
     * 测试isSameTimeNature - 判断同一时间维度.
     */
    @Test
    public void testIsSameTimeNature() {
        Long ts1 = parseTimestamp("2023-06-15 14:30:45");
        Long ts2 = parseTimestamp("2023-06-15 14:50:20");

        // 同一天，应该返回true
        assertTrue(DateTimeStamp.isSameTimeNature(ts1, ts2, TimeAlignmentEnum.DAY_1.getCode()));

        // 不同天，应该返回false
        Long ts3 = parseTimestamp("2023-06-16 14:30:45");
        assertFalse(DateTimeStamp.isSameTimeNature(ts1, ts3, TimeAlignmentEnum.DAY_1.getCode()));

        // 同一小时，应该返回true
        assertTrue(DateTimeStamp.isSameTimeNature(ts1, ts2, TimeAlignmentEnum.HOUR_1.getCode()));

        // 不同小时，应该返回false
        Long ts4 = parseTimestamp("2023-06-15 15:30:45");
        assertFalse(DateTimeStamp.isSameTimeNature(ts1, ts4, TimeAlignmentEnum.HOUR_1.getCode()));
    }

    /**
     * 测试getTimeFormat - 时间戳转字符串.
     */
    @Test
    public void testGetTimeFormat() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        String result = DateTimeStamp.getTimeFormat(timestamp);
        assertEquals("2023-06-15 14:30:45", result);
    }

    /**
     * 测试getTimeDate - 时间戳转Date.
     */
    @Test
    public void testGetTimeDate() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Date date = DateTimeStamp.getTimeDate(timestamp);
        assertNotNull(date);
        assertEquals(timestamp.longValue(), date.getTime());
    }

    /**
     * 测试insertTimeAdd8 - +8小时修正.
     */
    @Test
    public void testInsertTimeAdd8() {
        Long timestamp = parseTimestamp("2023-06-15 14:00:00");
        Long result = DateTimeStamp.insertTimeAdd8(timestamp);
        // +8小时后应该是次日的某个时间
        assertTrue(result > timestamp);
    }

    /**
     * 测试selectTimeReduce8 - -8小时修正.
     */
    @Test
    public void testSelectTimeReduce8() {
        Long timestamp = parseTimestamp("2023-06-15 14:00:00");
        Long result = DateTimeStamp.selectTimeReduce8(timestamp);
        // -8小时前应该是当天的某个时间
        assertTrue(result < timestamp);
    }

    /**
     * 测试按5分钟取整.
     */
    @Test
    public void testGetTimeByMinute5() {
        // 2023-06-15 14:07:30 -> 2023-06-15 14:05:00
        Long timestamp = parseTimestamp("2023-06-15 14:07:30");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MINUTE_5.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:05:00", resultStr);

        // 2023-06-15 14:08:00 -> 2023-06-15 14:05:00
        timestamp = parseTimestamp("2023-06-15 14:08:00");
        result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MINUTE_5.getCode());
        resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:05:00", resultStr);
    }

    /**
     * 测试按15分钟取整.
     */
    @Test
    public void testGetTimeByMinute15() {
        // 2023-06-15 14:20:00 -> 2023-06-15 14:15:00
        Long timestamp = parseTimestamp("2023-06-15 14:20:00");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MINUTE_15.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:15:00", resultStr);

        // 2023-06-15 14:30:00 -> 2023-06-15 14:30:00
        timestamp = parseTimestamp("2023-06-15 14:30:00");
        result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MINUTE_15.getCode());
        resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:30:00", resultStr);
    }

    /**
     * 测试月末.
     */
    @Test
    public void testMonthEnd() {
        // 2023-06-15 -> 2023-06-30
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_END.getCode());
        String resultStr = formatTimestamp(result);
        assertTrue(resultStr.startsWith("2023-06-30"));

        // 2023-02-15 -> 2023-02-28 (非闰年)
        timestamp = parseTimestamp("2023-02-15 14:30:45");
        result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_END.getCode());
        resultStr = formatTimestamp(result);
        assertTrue(resultStr.startsWith("2023-02-28"));

        // 2024-02-15 -> 2024-02-29 (闰年)
        timestamp = parseTimestamp("2024-02-15 14:30:45");
        result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_END.getCode());
        resultStr = formatTimestamp(result);
        assertTrue(resultStr.startsWith("2024-02-29"));
    }

    /**
     * 测试年末.
     */
    @Test
    public void testYearEnd() {
        // 2023-06-15 -> 2023-12-31
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.YEAR_END.getCode());
        String resultStr = formatTimestamp(result);
        assertTrue(resultStr.startsWith("2023-12-31"));
    }

    /**
     * 测试下一周期-天.
     */
    @Test
    public void testNextDay() {
        // 2023-06-15 14:30:45 -> 2023-06-16 00:00:00
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.DAY_1_NEXT.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-16 00:00:00", resultStr);
    }

    /**
     * 测试下一周期-月.
     */
    @Test
    public void testNextMonth() {
        // 2023-06-15 -> 2023-07-01
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.MONTH_1_NEXT.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2023-07-01 00:00:00", resultStr);
    }

    /**
     * 测试下一周期-年.
     */
    @Test
    public void testNextYear() {
        // 2023-06-15 -> 2024-01-01
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.YEAR_1_NEXT.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2024-01-01 00:00:00", resultStr);
    }

    /**
     * 测试原始时间（不做取整）.
     */
    @Test
    public void testOriginal() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");
        Long result = DateTimeStamp.getTime(timestamp, TimeAlignmentEnum.ORIGINAL.getCode());
        String resultStr = formatTimestamp(result);
        assertEquals("2023-06-15 14:30:45", resultStr);
    }

    /**
     * 测试字符串参数解析.
     */
    @Test
    public void testParseStringParameter() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");

        // 测试小写字符串
        Long result = DateTimeStamp.getTime(timestamp, "day_1");
        assertNotNull(result);

        // 测试大写字符串
        result = DateTimeStamp.getTime(timestamp, "F");
        assertNotNull(result);

        // 测试混合大小写
        result = DateTimeStamp.getTime(timestamp, "Day_1");
        assertNotNull(result);
    }

    /**
     * 测试无效枚举抛出异常.
     */
    @Test
    public void testInvalidEnumThrowsException() {
        Long timestamp = parseTimestamp("2023-06-15 14:30:45");

        assertThrows(RuntimeException.class, () -> {
            DateTimeStamp.getTime(timestamp, "invalid_enum");
        });

        assertThrows(RuntimeException.class, () -> {
            DateTimeStamp.getNextTimeNature("invalid_enum");
        });

        assertThrows(RuntimeException.class, () -> {
            DateTimeStamp.getAdd8TimeNature("invalid_enum");
        });
    }

    /**
     * 解析时间字符串为时间戳.
     */
    private Long parseTimestamp(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            return sdf.parse(dateStr).getTime();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse date: " + dateStr, e);
        }
    }

    /**
     * 格式化时间戳为字符串.
     */
    private String formatTimestamp(Long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(new Date(timestamp));
    }
}

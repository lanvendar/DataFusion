package com.datafusion.common.variable.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 函数参数解析器测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/06/23
 * @since 1.0.0
 */
class FunctionArgumentParserTest {

    @Test
    void shouldSplitSimpleArguments() {
        List<String> arguments = new FunctionArgumentParser().parse("_biz_date_, -1D, yyyyMMdd");

        assertEquals(List.of("_biz_date_", "-1D", "yyyyMMdd"), arguments);
    }

    @Test
    void shouldKeepCommaInsideSingleQuotedString() {
        List<String> arguments = new FunctionArgumentParser().parse("_biz_date_, 'a,b,c', yyyyMMdd");

        assertEquals(List.of("_biz_date_", "'a,b,c'", "yyyyMMdd"), arguments);
    }
}

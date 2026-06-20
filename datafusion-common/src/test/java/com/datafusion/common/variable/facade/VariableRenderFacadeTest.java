package com.datafusion.common.variable.facade;

import com.datafusion.common.variable.VariableRenderFacade;
import com.datafusion.common.variable.builtin.BuiltinVariableEnum;
import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.function.BuiltinFunc;
import com.datafusion.common.variable.function.BuiltinFuncFactory;
import com.datafusion.common.variable.VariableUtils;
import com.datafusion.scheduler.model.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 变量渲染门面测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class VariableRenderFacadeTest {

    /**
     * 测试时间: 2022-06-20 13:05:00.
     */
    private static final Long TEST_SCHEDULE_TIME = 1655708700000L;

    @Test
    public void testRenderVariable() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", VariableUtils.createVariable("table", "orders"));
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = new VariableRenderFacade().render("select * from #(table)", context);

        assertEquals("select * from orders", result);
    }

    @Test
    public void testRenderUnknownVariableKeepsRawToken() {
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = new VariableRenderFacade().render("#(unknown_var)", context);

        assertEquals("#(unknown_var)", result);
    }

    @Test
    public void testRenderBuiltinTimeFunction() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                VariableUtils.createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = new VariableRenderFacade().render("#day(biz_date, \"yyyyMMdd\")", context);

        assertEquals("20220620", result);
    }

    @Test
    public void testRenderDayWithOffsetSuffixPattern() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("base_day", VariableUtils.createVariable("base_day", "2022-06-23"));
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = new VariableRenderFacade().render("#day(base_day, \"-2M\", \"MD\", \"yyyy-MM-dd\")", context);

        assertEquals("2022-04-30", result);
    }

    @Test
    public void testRenderDayCalculatesOffsetBeforeSuffix() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("base_day", VariableUtils.createVariable("base_day", "2022-06-23"));
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = new VariableRenderFacade().render("#day(base_day, \"-2M\", \"MS\", \"yyyy-MM-dd\")", context);

        assertEquals("2022-04-01", result);
    }

    @Test
    public void testRenderTimestamp() {
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = new VariableRenderFacade().render("#timestamp(schedule_time)", context);

        assertEquals(String.valueOf(TEST_SCHEDULE_TIME), result);
    }

    @Test
    public void testRenderUnknownFunctionThrows() {
        VariableRenderContext context = VariableRenderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        assertThrows(IllegalArgumentException.class, () -> new VariableRenderFacade().render("#unknown(a)", context));
    }

    @Test
    public void testBuiltinFuncFactorySupportsCustomFunction() {
        BuiltinFuncFactory factory = new BuiltinFuncFactory();
        factory.register(new BuiltinFunc() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public String call(List<String> arguments, VariableRenderContext context) {
                return "ok";
            }
        });

        String result = factory.getBuiltinFunc("custom").call(List.of(), null);

        assertEquals("ok", result);
    }
}

package com.datafusion.common.variable.function;

import com.datafusion.common.variable.SqlVariableRenderContext;
import com.datafusion.common.variable.VariableRenderContext;
import com.datafusion.scheduler.model.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 变量函数测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class VariableFunctionTest {

    /**
     * 测试时间: 2022-06-20 15:05:00.
     */
    private static final Long TEST_TEMPLATE_SQL_TIME = 1655708700000L;

    @Test
    public void testDayUsesTemplateSqlTime() {
        VariableRenderContext context = SqlVariableRenderContext.builder()
                .templateSqlTime(TEST_TEMPLATE_SQL_TIME)
                .variables(new HashMap<>())
                .build();

        String result = new DayVariableFunction().call(List.of(), context);

        assertEquals("20220620150500", result);
    }

    @Test
    public void testDayWithOffsetSuffixPattern() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("base_day", createVariable("base_day", "2022-06-23"));
        VariableRenderContext context = SqlVariableRenderContext.builder()
                .templateSqlTime(TEST_TEMPLATE_SQL_TIME)
                .variables(variables)
                .build();

        String result = new DayVariableFunction().call(List.of("base_day", "\"-2M\"", "\"MD\"", "\"yyyy-MM-dd\""),
                context);

        assertEquals("2022-04-30", result);
    }

    @Test
    public void testDayCalculatesOffsetBeforeSuffix() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("base_day", createVariable("base_day", "2022-06-23"));
        VariableRenderContext context = SqlVariableRenderContext.builder()
                .templateSqlTime(TEST_TEMPLATE_SQL_TIME)
                .variables(variables)
                .build();

        String result = new DayVariableFunction().call(List.of("base_day", "\"-2M\"", "\"MS\"", "\"yyyy-MM-dd\""),
                context);

        assertEquals("2022-04-01", result);
    }

    @Test
    public void testTimestampReadsVariableValue() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("schedule_time", createVariable("schedule_time", String.valueOf(TEST_TEMPLATE_SQL_TIME)));
        VariableRenderContext context = SqlVariableRenderContext.builder()
                .templateSqlTime(TEST_TEMPLATE_SQL_TIME)
                .variables(variables)
                .build();

        String result = new TimestampVariableFunction().call(List.of("schedule_time"), context);

        assertEquals(String.valueOf(TEST_TEMPLATE_SQL_TIME), result);
    }

    @Test
    public void testDefaultRegistrySupportsCustomFunction() {
        DefaultVariableFunctionRegistry registry = new DefaultVariableFunctionRegistry();
        registry.register(new VariableFunction() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public String call(List<String> arguments, VariableRenderContext context) {
                return "ok";
            }
        });

        String result = registry.getFunction("custom").call(List.of(), null);

        assertEquals("ok", result);
    }

    /**
     * 创建变量.
     *
     * @param name  变量名
     * @param value 变量值
     * @return 变量
     */
    private Variable createVariable(String name, String value) {
        Variable variable = new Variable();
        variable.setName(name);
        variable.setValue(value);
        return variable;
    }
}

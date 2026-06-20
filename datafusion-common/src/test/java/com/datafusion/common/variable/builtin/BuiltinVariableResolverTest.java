package com.datafusion.common.variable.builtin;

import com.datafusion.common.variable.VariableUtils;
import com.datafusion.scheduler.model.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 内置变量解析器测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class BuiltinVariableResolverTest {

    /**
     * 测试时间: 2022-06-20 13:05:00.
     */
    private static final Long TEST_SCHEDULE_TIME = 1655708700000L;

    @Test
    public void testResolveBuiltinVariablesWithBizAndEventAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                VariableUtils.createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        variables.put(BuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                VariableUtils.createVariable(BuiltinVariableEnum.EVENT_ALIGN.getParamName(), "hour_1"));
        VariableRenderContext context = new VariableRenderContext();
        context.setScheduleTime(TEST_SCHEDULE_TIME);
        context.setVariables(variables);

        new BuiltinVariableResolver().resolveBuiltinVariables(context);

        assertEquals("day_1", variables.get(BuiltinVariableEnum.BIZ_ALIGN.getParamName()).getValue());
        assertEquals("hour_1", variables.get(BuiltinVariableEnum.EVENT_ALIGN.getParamName()).getValue());
        assertEquals("1655654400000", variables.get(BuiltinVariableEnum.BIZ_TIME.getParamName()).getValue());
        assertEquals("1655708400000", variables.get(BuiltinVariableEnum.EVENT_TIME.getParamName()).getValue());
        assertEquals("20220620000000", variables.get(BuiltinVariableEnum.BIZ_DATE.getParamName()).getValue());
        assertEquals("20220620150000", variables.get(BuiltinVariableEnum.EVENT_DATE.getParamName()).getValue());
    }

    @Test
    public void testResolveBuiltinVariablesDefaultAlign() {
        Map<String, Variable> variables = new HashMap<>();
        VariableRenderContext context = new VariableRenderContext();
        context.setScheduleTime(TEST_SCHEDULE_TIME);
        context.setVariables(variables);

        new BuiltinVariableResolver().resolveBuiltinVariables(context);

        assertEquals("original", variables.get(BuiltinVariableEnum.BIZ_ALIGN.getParamName()).getValue());
        assertEquals("original", variables.get(BuiltinVariableEnum.EVENT_ALIGN.getParamName()).getValue());
        assertEquals(String.valueOf(TEST_SCHEDULE_TIME), variables.get(BuiltinVariableEnum.BIZ_TIME.getParamName()).getValue());
        assertEquals(String.valueOf(TEST_SCHEDULE_TIME), variables.get(BuiltinVariableEnum.EVENT_TIME.getParamName()).getValue());
        assertNotNull(variables.get(BuiltinVariableEnum.NOW_TIME.getParamName()));
        assertNotNull(variables.get(BuiltinVariableEnum.NOW_DATE.getParamName()));
    }

    @Test
    public void testResolveBuiltinVariablesInvalidAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                VariableUtils.createVariable(BuiltinVariableEnum.EVENT_ALIGN.getParamName(), "bad_align"));
        VariableRenderContext context = new VariableRenderContext();
        context.setScheduleTime(TEST_SCHEDULE_TIME);
        context.setVariables(variables);

        assertThrows(IllegalArgumentException.class,
                () -> new BuiltinVariableResolver().resolveBuiltinVariables(context));
    }
}

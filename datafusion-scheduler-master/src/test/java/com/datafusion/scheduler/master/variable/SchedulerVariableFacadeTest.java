package com.datafusion.scheduler.master.variable;

import com.datafusion.scheduler.model.Variable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Scheduler variable 模块单元测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
public class SchedulerVariableFacadeTest {

    private static final Logger log = LoggerFactory.getLogger(SchedulerVariableFacadeTest.class);

    /**
     * 测试时间: 2022/06/20 13:05:00
     */
    private static final Long TEST_SCHEDULE_TIME = 1655708700000L;

    @Test
    public void testSchedulerVariableResolverBizDateWithAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        SchedulerVariableResolver resolver = new SchedulerVariableResolver();
        resolver.resolveBuiltinVariables(context);

        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_DATE.getParamName()));
        String bizDate = context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_DATE.getParamName()).getValue();
        log.info("biz_date with align: {}", bizDate);
        assertNotNull(bizDate);
    }

    @Test
    public void testSchedulerVariableResolverBizDateWithoutAlign() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        SchedulerVariableResolver resolver = new SchedulerVariableResolver();
        resolver.resolveBuiltinVariables(context);

        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_DATE.getParamName()));
        String bizDate = context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_DATE.getParamName()).getValue();
        log.info("biz_date without align: {}", bizDate);
        assertNotNull(bizDate);
    }

    @Test
    public void testSchedulerVariableResolverAllVariables() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        variables.put(SchedulerBuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.EVENT_ALIGN.getParamName(), "hour_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        SchedulerVariableResolver resolver = new SchedulerVariableResolver();
        resolver.resolveBuiltinVariables(context);

        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.NOW_TIME.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.NOW_DATE.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.SCHEDULE_TIME.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_TIME.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.BIZ_DATE.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.EVENT_ALIGN.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.EVENT_TIME.getParamName()));
        assertNotNull(context.getVariables().get(SchedulerBuiltinVariableEnum.EVENT_DATE.getParamName()));
    }

    @Test
    public void testFacadeSimpleVariable() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));

        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("SELECT * FROM #(table)", context);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacadeNullContext() {
        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("SELECT * FROM orders", null);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacadeNullValue() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders(null, context);
        assertNull(result);
    }

    @Test
    public void testFacadeEmptyValue() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("", context);
        assertEquals("", result);
    }

    @Test
    public void testFacadeNoPlaceholder() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("SELECT * FROM orders WHERE status = 'active'", context);
        assertEquals("SELECT * FROM orders WHERE status = 'active'", result);
    }

    @Test
    public void testFacadeNewSyntaxVariable() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("SELECT * FROM #(table)", context);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacadeUnknownVariableKeepsRawToken() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("#(unknown_var)", context);
        assertEquals("#(unknown_var)", result);
    }

    @Test
    public void testFacadeOldSyntaxNotSupported() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("#{table}|#[DAY(biz_date)]", context);
        assertEquals("#{table}|#[DAY(biz_date)]", result);
    }

    @Test
    public void testFacadeNewSyntaxDay() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("#day(biz_date, \"yyyyMMdd\")", context);
        assertEquals("20220620", result);
    }

    @Test
    public void testFacadeExpressionWithBizAndEventAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        variables.put(SchedulerBuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                createVariable(SchedulerBuiltinVariableEnum.EVENT_ALIGN.getParamName(), "hour_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("#day(biz_date)|#day(event_date)", context);
        assertEquals("20220620000000|20220620150000", result);
    }

    @Test
    public void testFacadeTimestamp() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = SchedulerVariableFacade.getInstance().replacePlaceholders("#timestamp(schedule_time)", context);
        assertEquals(String.valueOf(TEST_SCHEDULE_TIME), result);
    }

    @Test
    public void testFacadeUnknownFunctionThrows() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> SchedulerVariableFacade.getInstance().replacePlaceholders("#unknown(a)", context));
    }

    // ==================== 辅助方法 ====================

    private Variable createVariable(String name, String value) {
        Variable var = new Variable();
        var.setName(name);
        var.setValue(value);
        return var;
    }
}

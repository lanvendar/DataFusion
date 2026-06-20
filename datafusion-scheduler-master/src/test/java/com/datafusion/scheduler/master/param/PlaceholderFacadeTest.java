package com.datafusion.scheduler.master.param;

import com.datafusion.common.variable.builtin.BuiltinVariableEnum;
import com.datafusion.scheduler.master.param.builtin.BuiltinParamResolver;
import com.datafusion.scheduler.model.Variable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Param 模块单元测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
public class PlaceholderFacadeTest {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderFacadeTest.class);

    /**
     * 测试时间: 2022/06/20 13:05:00
     */
    private static final Long TEST_SCHEDULE_TIME = 1655708700000L;

    // ==================== BuiltinParamResolver 测试 ====================

    @Test
    public void testBuiltinParamResolver_BizDateWithAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        BuiltinParamResolver resolver = new BuiltinParamResolver();
        resolver.resolveBuiltinParams(context);

        assertNotNull(context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()));
        String bizDate = context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()).getValue();
        log.info("biz_date with align: {}", bizDate);
        assertNotNull(bizDate);
    }

    @Test
    public void testBuiltinParamResolver_BizDateWithoutAlign() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        BuiltinParamResolver resolver = new BuiltinParamResolver();
        resolver.resolveBuiltinParams(context);

        assertNotNull(context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()));
        String bizDate = context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()).getValue();
        log.info("biz_date without align: {}", bizDate);
        assertNotNull(bizDate);
    }

    @Test
    public void testBuiltinParamResolver_AllParams() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        variables.put(BuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.EVENT_ALIGN.getParamName(), "hour_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        BuiltinParamResolver resolver = new BuiltinParamResolver();
        resolver.resolveBuiltinParams(context);

        assertNotNull(context.getVariables().get(BuiltinVariableEnum.NOW_TIME.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.NOW_DATE.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.SCHEDULE_TIME.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.BIZ_ALIGN.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.BIZ_TIME.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.EVENT_ALIGN.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.EVENT_TIME.getParamName()));
        assertNotNull(context.getVariables().get(BuiltinVariableEnum.EVENT_DATE.getParamName()));

        log.info("NOW_TIME: {}", context.getVariables().get(BuiltinVariableEnum.NOW_TIME.getParamName()).getValue());
        log.info("NOW_DATE: {}", context.getVariables().get(BuiltinVariableEnum.NOW_DATE.getParamName()).getValue());
        log.info("SCHEDULE_TIME: {}", context.getVariables().get(BuiltinVariableEnum.SCHEDULE_TIME.getParamName()).getValue());
        log.info("BIZ_ALIGN: {}", context.getVariables().get(BuiltinVariableEnum.BIZ_ALIGN.getParamName()).getValue());
        log.info("BIZ_TIME: {}", context.getVariables().get(BuiltinVariableEnum.BIZ_TIME.getParamName()).getValue());
        log.info("BIZ_DATE: {}", context.getVariables().get(BuiltinVariableEnum.BIZ_DATE.getParamName()).getValue());
        log.info("EVENT_ALIGN: {}", context.getVariables().get(BuiltinVariableEnum.EVENT_ALIGN.getParamName()).getValue());
        log.info("EVENT_TIME: {}", context.getVariables().get(BuiltinVariableEnum.EVENT_TIME.getParamName()).getValue());
        log.info("EVENT_DATE: {}", context.getVariables().get(BuiltinVariableEnum.EVENT_DATE.getParamName()).getValue());
    }

    // ==================== PlaceholderFacade 测试 ====================

    @Test
    public void testFacade_SimpleVariable() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));

        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("SELECT * FROM #(table)", context);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacade_NullContext() {
        String result = PlaceholderFacade.getInstance().replacePlaceholders("SELECT * FROM orders", null);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacade_NullValue() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders(null, context);
        assertNull(result);
    }

    @Test
    public void testFacade_EmptyValue() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("", context);
        assertEquals("", result);
    }

    @Test
    public void testFacade_NoPlaceholder() {
        Map<String, Variable> variables = new HashMap<>();
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("SELECT * FROM orders WHERE status = 'active'", context);
        assertEquals("SELECT * FROM orders WHERE status = 'active'", result);
    }

    @Test
    public void testFacade_NewSyntaxVariable() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("SELECT * FROM #(table)", context);
        assertEquals("SELECT * FROM orders", result);
    }

    @Test
    public void testFacade_UnknownVariableKeepsRawToken() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("#(unknown_var)", context);
        assertEquals("#(unknown_var)", result);
    }

    @Test
    public void testFacade_OldSyntaxNotSupported() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put("table", createVariable("table", "orders"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("#{table}|#[DAY(biz_date)]", context);
        assertEquals("#{table}|#[DAY(biz_date)]", result);
    }

    @Test
    public void testFacade_NewSyntaxDay() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("#day(biz_date, \"yyyyMMdd\")", context);
        assertEquals("20220620", result);
    }

    @Test
    public void testFacade_ExpressionWithBizAndEventAlign() {
        Map<String, Variable> variables = new HashMap<>();
        variables.put(BuiltinVariableEnum.BIZ_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.BIZ_ALIGN.getParamName(), "day_1"));
        variables.put(BuiltinVariableEnum.EVENT_ALIGN.getParamName(),
                createVariable(BuiltinVariableEnum.EVENT_ALIGN.getParamName(), "hour_1"));
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(variables)
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("#day(biz_date)|#day(event_date)", context);
        assertEquals("20220620000000|20220620150000", result);
    }

    @Test
    public void testFacade_Timestamp() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        String result = PlaceholderFacade.getInstance().replacePlaceholders("#timestamp(schedule_time)", context);
        assertEquals(String.valueOf(TEST_SCHEDULE_TIME), result);
    }

    @Test
    public void testFacade_UnknownFunctionThrows() {
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(TEST_SCHEDULE_TIME)
                .variables(new HashMap<>())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> PlaceholderFacade.getInstance().replacePlaceholders("#unknown(a)", context));
    }

    // ==================== 辅助方法 ====================

    private Variable createVariable(String name, String value) {
        Variable var = new Variable();
        var.setName(name);
        var.setValue(value);
        return var;
    }
}

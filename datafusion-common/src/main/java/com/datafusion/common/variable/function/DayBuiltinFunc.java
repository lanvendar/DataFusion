package com.datafusion.common.variable.function;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.builtin.BuiltinTimeParams;

import java.util.Date;
import java.util.List;

/**
 * day 内置函数.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class DayBuiltinFunc extends AbstractTimeBuiltinFunc {

    @Override
    public String name() {
        return "day";
    }

    @Override
    public String call(List<String> arguments, VariableRenderContext context) {
        if (arguments.size() > 4) {
            throw new IllegalArgumentException("Wrong number parameter of #day directive");
        }
        String base = argument(arguments, 0);
        String offset = null;
        String suffix = null;
        String pattern = BuiltinTimeParams.DEFAULT_PATTERN;
        if (arguments.size() == 2) {
            String second = argument(arguments, 1);
            if (DateCalUtil.isOffsetExp(second)) {
                offset = second;
            } else {
                pattern = second;
            }
        } else if (arguments.size() == 3) {
            offset = argument(arguments, 1);
            String third = argument(arguments, 2);
            if (DateCalUtil.isSuffixExp(third)) {
                suffix = third;
            } else {
                pattern = third;
            }
        } else if (arguments.size() == 4) {
            offset = argument(arguments, 1);
            suffix = argument(arguments, 2);
            pattern = argument(arguments, 3);
        }
        Date baseDate = resolveBaseDate(base, context);
        if (baseDate == null) {
            return null;
        }
        Date calculated = DateCalUtil.calDateExp(baseDate, offset, suffix);
        return DateCalUtil.format(calculated, pattern);
    }
}

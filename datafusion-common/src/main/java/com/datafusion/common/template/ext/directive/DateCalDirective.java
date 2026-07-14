package com.datafusion.common.template.ext.directive;

import com.datafusion.common.variable.SqlVariableRenderContext;
import com.datafusion.common.variable.function.DayVariableFunction;
import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.expr.ast.Expr;
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * #day 日期格式计算,默认返回日期格式 yyyyMMdd 对 {@link com.jfinal.template.ext.directive.DateDirective} 扩展.
 *
 * <p>四种用法：</p>
 *
 * <p>1：#day(createAt, '-2M', 'MD', 'yyyyMMdd') 根据 createAt，按规则计算日期</p>
 *
 * <p>2：#day(createAt) 用默认 datePattern 配置，输出 createAt 变量中的日期值</p>
 *
 * <p>3：#day(createAt, "yyyy-MM-dd") 用第二个参数指定的 datePattern，输出 createAt 变量中的日期值</p>
 *
 * <p>4：#day() 用默认 datePattern 配置，输出 “当前” 日期值</p>
 *
 * <p>注意：</p>
 *
 * <p>1：#day 指令中的参数可以是变量，例如：#day(d, p) 中的 d 与 p 可以全都是变量</p>
 *
 * <p>2：默认 datePattern 可通过 Engine.setDatePattern(...) 进行配置</p>
 *
 * <p>3：jfinal 4.9.02 版新增支持 java 8 的 LocalDateTime、LocalDate、LocalTime</p>
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2022/01/25
 */
public class DateCalDirective extends Directive {

    /**
     * 日期函数.
     */
    private final DayVariableFunction dayVariableFunction = new DayVariableFunction();

    /**
     * 参数表达式列表.
     */
    private ExprList exprList;

    /**
     * 校验表达式.
     *
     * @param exprList 表达式
     */
    @Override
    public void setExprList(ExprList exprList) {
        int paraNum = exprList.length();
        if (paraNum > 4) {
            throw new ParseException("Wrong number parameter of #day directive, four parameters allowed at most", location);
        }
        this.exprList = exprList;
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        String writeDate = dayVariableFunction.call(resolveArguments(scope), SqlVariableRenderContext.builder()
                .templateSqlTime(System.currentTimeMillis())
                .build());
        //写入模板
        write(writer, writeDate);
    }

    /**
     * 解析参数.
     *
     * @param scope 作用域
     * @return 参数列表
     */
    private List<String> resolveArguments(Scope scope) {
        List<String> arguments = new ArrayList<>();
        if (exprList == null) {
            return arguments;
        }
        for (int i = 0; i < exprList.length(); i++) {
            Expr expr = exprList.getExpr(i);
            Object value = expr.eval(scope);
            arguments.add(value == null ? null : value.toString());
        }
        return arguments;
    }
}

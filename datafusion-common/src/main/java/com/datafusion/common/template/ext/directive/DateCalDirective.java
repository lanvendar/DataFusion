package com.datafusion.common.template.ext.directive;

import com.datafusion.common.date.DateCalUtil;
import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.expr.ast.Expr;
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;

import java.util.Date;

/**
 * #day 日期格式计算,默认返回日期格式 yyyyMMdd 对 {@link com.jfinal.template.ext.directive.DateDirective} 扩展.
 * <p>四种用法：</p>
 * <p>1：#day(createAt,'-2M', 'YYYYMM', 'ED') 根据createAt，按规则计算日期</p>
 * <p>2：#day(createAt) 用默认 datePattern 配置，输出 createAt 变量中的日期值</p>
 * <p>3：#day(createAt, "yyyy-MM-dd") 用第二个参数指定的 datePattern，输出 createAt 变量中的日期值</p>
 * <p>4：#day() 用默认 datePattern 配置，输出 “当前” 日期值</p>
 * <p>注意：</p>
 * <p>1：#day 指令中的参数可以是变量，例如：#day(d, p) 中的 d 与 p 可以全都是变量</p>
 * <p>2：默认 datePattern 可通过 Engine.setDatePattern(...) 进行配置</p>
 * <p>3：jfinal 4.9.02 版新增支持 java 8 的 LocalDateTime、LocalDate、LocalTime</p>
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2022/01/25
 */
public class DateCalDirective extends Directive {

    /**
     * 日期参数.
     */
    private Expr dateExpr;

    /**
     * 规则参数. {@link DateCalUtil}
     */
    private Expr ruleExpr;

    /**
     * 日期格式. {@link DateCalUtil}
     */
    private Expr patternExpr;

    /**
     * 日期结尾参数. {@link DateCalUtil}
     */
    private Expr suffixExpr;

    /**
     * 默认日期格式.
     */
    private static final String DEFAULT_PATTERN = "yyyyMMdd";

    /**
     * 校验表达式.
     *
     * @param exprList 表达式
     */
    @Override
    public void setExprList(ExprList exprList) {
        int paraNum = exprList.length();
        if (paraNum == 0) {
            dateExpr = null;
            ruleExpr = null;
            patternExpr = null;
            suffixExpr = null;
        } else if (paraNum == 1) {
            dateExpr = exprList.getExpr(0);
            ruleExpr = null;
            patternExpr = null;
            suffixExpr = null;
        } else if (paraNum == 2) {
            dateExpr = exprList.getExpr(0);
            ruleExpr = exprList.getExpr(1);
            patternExpr = null;
        } else if (paraNum == 3) {
            dateExpr = exprList.getExpr(0);
            ruleExpr = exprList.getExpr(1);
            patternExpr = exprList.getExpr(2);
        } else if (paraNum == 4) {
            dateExpr = exprList.getExpr(0);
            ruleExpr = exprList.getExpr(1);
            patternExpr = exprList.getExpr(2);
            suffixExpr = exprList.getExpr(3);
        } else {
            throw new ParseException("Wrong number parameter of #day directive, two parameters allowed at most",
                    location);
        }
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        Date date = null;
        String offset = null;
        String pattern = DEFAULT_PATTERN;

        if (dateExpr == null) {
            date = new Date();
        } else {
            date = DateCalUtil.checkStringDate((dateExpr.eval(scope).toString()));
        }
        if (ruleExpr != null) {
            offset = ruleExpr.eval(scope).toString();
        }
        if (patternExpr != null) {
            pattern = patternExpr.eval(scope).toString();
        }
        String suffix = null;
        if (suffixExpr != null) {
            suffix = suffixExpr.eval(scope).toString();
        }

        String writeDate = DateCalUtil.calDateExpFormat(date, offset, suffix, pattern);
        //写入模板
        write(writer, writeDate);
    }
}

/**
 * Copyright (c) 2011-2019, James Zhan 詹波 (jfinal@126.com).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package com.datafusion.common.template.ext.directive;

import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.TemplateException;
import com.jfinal.template.expr.ast.Const;
import com.jfinal.template.expr.ast.Expr;
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;

/**
 * NameSpaceDirective.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2019/12/12
 */
public class NameSpaceDirective extends Directive {
    
    /**
     * 此参数为sql模板定位key参数,已被用于自定义扩展模板取值.
     */
    public static final String NAME_SPACE_KEY = "_NAME_SPACE_";
    
    /**
     * 域路径.
     */
    private String nameSpace;
    
    @Override
    public void setExprList(ExprList exprList) {
        if (exprList.length() == 0) {
            throw new ParseException("The parameter of #namespace directive can not be blank", location);
        }
        if (exprList.length() > 1) {
            throw new ParseException("Only one parameter allowed for #namespace directive", location);
        }
        Expr expr = exprList.getExpr(0);
        if (!(expr instanceof Const && ((Const) expr).isStr())) {
            throw new ParseException("The parameter of #namespace directive must be String", location);
        }
        nameSpace = ((Const) expr).getStr();
    }
    
    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        if (scope.get(NAME_SPACE_KEY) != null) {
            throw new TemplateException("#namespace directive can not be nested", location);
        }
        scope.set(NAME_SPACE_KEY, nameSpace);
        try {
            stat.exec(env, scope, writer);
        } finally {
            scope.remove(NAME_SPACE_KEY);
        }
    }
    
    @Override
    public boolean hasEnd() {
        return true;
    }
}






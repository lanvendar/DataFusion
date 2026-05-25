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
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.expr.ast.Id;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;

import java.util.ArrayList;

/**
 * #p 指令用于在 sql 模板中根据参数名生成问号占位符.
 *
 * <pre>
 * 一、参数为表达式的用法
 * 1：模板内容
 *   #sql("find")
 *     select * from user where nickName = #para(nickName) and age > #para(age)
 *   #end
 *
 * 2： java 代码
 *   SqlPara sp = getSqlPara("find", Kv.by("nickName", "prettyGirl").set("age", 18));
 *   user.find(sp)
 *   或者：
 *   user.find(sp.getSql(), sp.getPara());
 *
 * 3：以上用法会在 #para(expr) 处生成问号占位字符，并且实际的参数放入 SqlPara 对象的参数列表中
 *   后续可以通过 sqlPara.getPara() 获取到参数并直接用于查询
 *
 *
 * 二、参数为 int 型数字的用法
 * 1：模板内容
 *   #sql("find")
 *     select * from user where id > #para(0) and id < #para(1)
 *   #end
 *
 * 2： java 代码
 *   SqlPara sp = getSqlPara("find", 10, 100);
 *   user.find(sp)
 *
 * 3：以上用法会在 #para(0) 与 #para(1) 处生成问号占位字符，并且将 10、100 这两个参数放入
 *    SqlPara 对象的参数列表中，后续可以通过 sqlPara.getPara() 获取到参数并直接用于查询
 * </pre>
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2019/12/12
 */
public class PlaceHolderDirective extends Directive {
    
    /**
     * 此参数为sql参数键,已被用于自定义扩展模板取值.
     */
    public static final String SQL_PARAMS_KEY = "_SQL_PARAMS_KEY_";
    
    /**
     * 模板参数名称.
     */
    private String paraName = null;
    
    /**
     * 模板参数校验对象.
     */
    private static boolean checkParaAssigned = true;
    
    /**
     * 参数名称检查标志.
     *
     * @param checkParaAssigned true:参数名称不能为空
     */
    public static void setCheckParaAssigned(boolean checkParaAssigned) {
        PlaceHolderDirective.checkParaAssigned = checkParaAssigned;
    }
    
    @Override
    public void setExprList(ExprList exprList) {
        if (exprList.length() == 0) {
            throw new ParseException("The parameter of #p directive can not be blank", location);
        }
        
        if (exprList.length() != 1) {
            throw new ParseException("The parameter #p must equal 1 parameter", location);
        }
        
        if (checkParaAssigned && exprList.getLastExpr() instanceof Id) {
            Id id = (Id) exprList.getLastExpr();
            paraName = id.getId();
        }
        
        this.exprList = exprList;
    }
    
    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        // #p(paraName) 中的 paraName 没有赋值时抛出异常
        if (checkParaAssigned && paraName != null && !scope.exists(paraName)) {
            throw new TemplateException("The parameter \"" + paraName + "\" must be assigned", location);
        }
        
        Object obj = scope.get(SQL_PARAMS_KEY);
        if (obj instanceof ArrayList) {
            ArrayList<String> list = (ArrayList<String>) scope.get(SQL_PARAMS_KEY);
            list.add(paraName);
        } else {
            throw new TemplateException("[SQL_PARAS_KEY] is must be of 'ArrayList<String>'.", location);
        }
        
        // #p() 标识的参数会自动替换成占位符 ?
        write(writer, "?");
    }
}




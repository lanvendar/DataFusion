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

import cn.hutool.core.text.CharSequenceUtil;
import com.datafusion.common.enums.CommandType;
import com.datafusion.common.enums.JdbcExecutionType;
import com.datafusion.common.enums.RenderType;
import com.datafusion.common.template.SqlTemplate;
import com.jfinal.kit.StrKit;
import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.Template;
import com.jfinal.template.expr.ast.Assign;
import com.jfinal.template.expr.ast.Expr;
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;

import java.util.Map;

/**
 * SqlDirective. 单独使用或者与 {@link NameSpaceDirective} 联合使用
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/04/11
 * @since 2019/12/12
 */
public class SqlDirective extends Directive {
    
    /**
     * 此参数为sql模板定位key参数,已被用于自定义扩展模板取值.
     */
    public static final String SQL_TEMPLATE_MAP_KEY = "_SQL_TEMPLATE_PATH_";
    
    /**
     * id 为SQL路径,必填.
     */
    private String id;
    
    /**
     * sqlCommand 为SQL执行类型,非必填,默认为 select.
     */
    private String commandType;
    
    /**
     * statementType 为SQL预编译类型,默认为 statement.
     */
    private String executionType;
    
    /**
     * renderType 为SQL渲染类型,非必填,默认为 normal.
     */
    private String renderType;
    
    //TODO #sql(id="key2",command="insert") 的 id,command 目前是两个,这里是可扩展的.
    
    @Override
    public void setExprList(ExprList exprList) {
        if (exprList.length() == 0) {
            throw new ParseException("The parameter of #sql directive can not be blank", location);
        }
        if (exprList.length() > 4) {
            throw new ParseException("Only two parameter allowed for #sql directive", location);
        }
        
        for (Expr expr : exprList.getExprArray()) {
            Assign assign = (Assign) expr;
            String key = assign.getId().toLowerCase();
            switch (key) {
                case "id":
                    id = ((Assign) expr).getRight().toString();
                    break;
                case "command":
                    commandType = (((Assign) expr).getRight().toString());
                    break;
                case "execution":
                    executionType = (((Assign) expr).getRight().toString());
                    break;
                case "render":
                    renderType = (((Assign) expr).getRight().toString());
                    break;
                default:
                    break;
            }
            //TODO #sql(id="key2",command="insert") 的 id,command 目前是两个,这里是可扩展的.
            if (CharSequenceUtil.isBlank(id)) {
                throw new ParseException("Name of id parameter is can not be blank", location);
            }
            if (CharSequenceUtil.isBlank(commandType)) {
                //默认为 select.
                commandType = "select";
            }
            if (CharSequenceUtil.isBlank(executionType)) {
                //默认为 statement.
                executionType = "statement";
            }
            if (CharSequenceUtil.isBlank(renderType)) {
                renderType = "normal";
            }
        }
    }
    
    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        String nameSpace = (String) scope.get(NameSpaceDirective.NAME_SPACE_KEY);
        String key = StrKit.isBlank(nameSpace) ? id : nameSpace + "." + id;
        Map<String, SqlTemplate> sqlTemplateMap = (Map<String, SqlTemplate>) scope.get(SQL_TEMPLATE_MAP_KEY);
        if (sqlTemplateMap.containsKey(key)) {
            throw new ParseException("Sql already exists with key : " + key, location);
        }
        //加载模板
        SqlTemplate sqlTemplate = new SqlTemplate();
        sqlTemplate.setCommandType(CommandType.fromValueOrThrow(commandType));
        sqlTemplate.setExecutionType(JdbcExecutionType.fromValue(executionType));
        sqlTemplate.setRenderType(RenderType.fromValue(renderType));
        sqlTemplate.setTemplate(new Template(env, stat));
        sqlTemplateMap.put(key, sqlTemplate);
    }
    
    @Override
    public boolean hasEnd() {
        return true;
    }
}




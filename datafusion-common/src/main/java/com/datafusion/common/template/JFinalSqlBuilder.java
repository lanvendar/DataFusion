package com.datafusion.common.template;

import cn.hutool.core.util.StrUtil;
import com.datafusion.common.enums.CommandType;
import com.datafusion.common.enums.JdbcExecutionType;
import com.datafusion.common.enums.RenderType;
import com.datafusion.common.template.ext.directive.PlaceHolderDirective;
import com.jfinal.template.Template;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JFinal enjoy sql 模板加载,模板初始化默认参数,sql渲染类.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2019/11/20
 */
@Slf4j
public class JFinalSqlBuilder {
    /**
     * 批量插入模板参数标识符.
     */
    public static final String SYMBOL_INSERT_ROWS = "_symbol_insert_rows_";
    
    /**
     * sql 模板创建器.
     */
    private SqlTemplateFactory sqlTemplateFactory;
    
    /**
     * 指令模板对象名称.
     */
    private String engineName;
    
    /**
     * 模板文件热加载.
     */
    private boolean devMode;
    
    /**
     * sql文件后缀名.
     */
    private String sqlFileSuffix;
    
    /**
     * 设置sql模板的相对路径.
     */
    private String sqlPath;
    
    /**
     * jar包路径.
     */
    private String jarPath;
    
    /**
     * 模板文件全路径.
     */
    private String dirPath;
    
    /**
     * 创建一个 Builder 对象.
     *
     * @return 构建 JFinalSqlBuilder 实例
     */
    public static Builder create() {
        return new Builder();
    }
    
    /**
     * 实现静态内部类 Builder.
     */
    public static class Builder {
        /**
         * 指令模板对象名称默认值.
         */
        private String engineName = "sql_operate";
        
        /**
         * 模板文件热加载默认值.
         */
        private boolean devMode = false;
        
        /**
         * sql文件后缀名默认值.
         */
        private String sqlFileSuffix = ".sql";
        
        /**
         * 设置sql的相对路径默认值.
         */
        private String sqlPath = "sql";
        
        /**
         * jar包路径.
         */
        private String jarPath;
        
        /**
         * 模板文件全路径.
         */
        private String dirPath;
        
        /**
         * 私有构造函数，防止外部直接创建 Builder.
         */
        private Builder() {
        }
        
        /**
         * 设置指令模板对象名称.
         *
         * @param engineName 指令模板对象名称
         * @return Builder 对象本身，用于链式调用
         */
        public Builder engineName(String engineName) {
            if (StrUtil.isNotBlank(engineName)) {
                this.engineName = engineName;
            }
            return this;
        }
        
        /**
         * 模板文件热加载.
         *
         * @param devMode 模板文件热加载
         * @return Builder 对象本身，用于链式调用
         */
        public Builder devMode(boolean devMode) {
            this.devMode = devMode;
            return this;
        }
        
        /**
         * sql文件后缀名.
         *
         * @param sqlFileSuffix sql文件后缀名
         * @return Builder 对象本身，用于链式调用
         */
        public Builder sqlFileSuffix(String sqlFileSuffix) {
            if (StrUtil.isNotBlank(sqlFileSuffix)) {
                this.sqlFileSuffix = sqlFileSuffix;
            }
            return this;
        }
        
        /**
         * 设置sql的相对路径.
         *
         * @param sqlTplPath sql的相对路径
         * @return Builder 对象本身，用于链式调用
         */
        public Builder sqlTplPath(String sqlTplPath) {
            this.sqlPath = sqlTplPath; // 允许为null或空，在加载时判断
            return this;
        }
        
        /**
         * jar包路径.
         *
         * @param jarPath jar包路径
         * @return Builder 对象本身，用于链式调用
         */
        public Builder jarPath(String jarPath) {
            this.jarPath = jarPath;
            return this;
        }
        
        /**
         * 模板文件全路径.
         *
         * @param dirPath 模板文件全路径
         * @return Builder 对象本身，用于链式调用
         */
        public Builder dirPath(String dirPath) {
            this.dirPath = dirPath;
            return this;
        }
        
        /**
         * 最终的构建方法，完成对象的创建和初始化.
         *
         * @return 一个完全配置并初始化好的 JFinalSqlBuilder 实例。
         */
        public JFinalSqlBuilder build() {
            JFinalSqlBuilder instance = new JFinalSqlBuilder();
            
            // 将 Builder 中配置好的值赋给最终的实例
            instance.engineName = this.engineName;
            instance.devMode = this.devMode;
            instance.sqlFileSuffix = this.sqlFileSuffix;
            instance.sqlPath = this.sqlPath;
            instance.jarPath = this.jarPath;
            instance.dirPath = this.dirPath;
            
            //在所有属性都设置好之后，调用初始化方法
            instance.initialize();
            return instance;
        }
    }
    
    /**
     * 初始化核心工厂并加载所有SQL模板.
     * 这个方法由私有构造函数调用，确保在对象完全可用前完成所有设置。
     */
    private void initialize() {
        // 使用字段值来初始化工厂
        this.sqlTemplateFactory = new SqlTemplateFactory(this.engineName, this.devMode);
        
        // 加载模板
        loadSqlTemplates();
        
        // 解析模板
        this.sqlTemplateFactory.renderSqlTemplate();
        log.info("初始化完成,sql模板总数: {}", sqlTemplateFactory.sqlTemplateTotalCnt());
    }
    
    /**
     * 扫描所有来源的SQL模板并添加到工厂中.
     */
    private void loadSqlTemplates() {
        // 1. 默认总是扫描 classpath
        SqlSourceScanner.Scanner scanner = SqlSourceScanner.scan()//
                .fromClasspath(this.sqlPath)//
                .withSuffix(this.sqlFileSuffix);
        
        // 2. 如果用户配置了外部目录，也添加进来
        if (StrUtil.isNotBlank(this.dirPath)) {
            scanner.fromDirectory(this.dirPath);
        }
        
        // 3. 如果用户配置了外部JAR，也添加进来
        if (StrUtil.isNotBlank(this.jarPath)) {
            // 假设在特定JAR内部，也查找名为 this.sqlPath 的目录
            scanner.fromJar(this.jarPath, this.sqlPath);
        }
        
        Set<SqlSource> sources = scanner.execute();
        sources.forEach(sqlTemplateFactory::addSqlTemplate);
    }
    
    /**
     * 获取最终构建好的 SqlTemplateFactory.
     * 必须在调用 build() 方法之后调用此方法.
     *
     * @return SqlTemplateFactory 实例
     */
    public SqlTemplateFactory getSqlTemplateFactory() {
        if (this.sqlTemplateFactory == null) {
            throw new IllegalStateException("必须先调用 build() 方法进行初始化！");
        }
        return this.sqlTemplateFactory;
    }
    
    /**
     * 带参数的单条 Sql 渲染.
     *
     * @param sqlKeyPath 模板路径 string
     * @param params     参数
     * @return 单条 Sql 和预编译参数
     */
    public SqlParamRender renderSql(String sqlKeyPath, Map<String, Object> params) {
        SqlTemplate sqlTemplate = sqlTemplateFactory.getSqlTemplate(sqlKeyPath);
        Template template = sqlTemplate.getTemplate();
        if (template == null) {
            return null;
        }
        
        RenderType renderType = sqlTemplate.getRenderType();
        SqlParamRender render;
        
        // 特殊情况处理,TODO 也可以与现有的 getSymbolRender合并,在 getSymbolRender 中再分单个和批量参数
        if (renderType == RenderType.SYMBOL && params != null && params.containsKey(SYMBOL_INSERT_ROWS)) {
            Object rowParams = params.get(SYMBOL_INSERT_ROWS);
            if (rowParams instanceof List) {
                log.debug("检测到SYMBOL批量渲染场景sqlKeyPath: {}", sqlKeyPath);
                render = getSymbolParamsRender(template, (List<Map<String, Object>>) rowParams);
            } else {
                throw new IllegalArgumentException("'_symbol_insert_rows_' key的值必须是 List<Map<String, Object>> 类型");
            }
        } else {
            // 所有其他单次渲染的情况
            switch (renderType) {
                case ORIGINAL:
                    render = getOriginalRender(template);
                    break;
                case NORMAL:
                    render = getNormalRender(template, params);
                    break;
                case SYMBOL:
                    render = getSymbolRender(template, params);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的渲染类型: " + renderType);
            }
        }
        
        render.setCommandType(sqlTemplate.getCommandType());
        render.setExecutionType(sqlTemplate.getExecutionType());
        render.setRenderType(renderType);
        return render;
    }
    
    /**
     * 带参数的多条 Sql 渲染.
     *
     * @param sqlKeyPath sql模板路径
     * @param struct     参数
     * @return 多条 Sql
     */
    public List<SqlParamRender> renderSqlBatch(String sqlKeyPath, List<Map<String, Object>> struct) {
        SqlTemplate sqlTemplate = sqlTemplateFactory.getSqlTemplate(sqlKeyPath);
        Template template = sqlTemplate.getTemplate();
        if (template == null) {
            return null;
        }
        CommandType commandType = sqlTemplate.getCommandType();
        JdbcExecutionType executionType = sqlTemplate.getExecutionType();
        RenderType renderType = sqlTemplate.getRenderType();
        List<SqlParamRender> renders;
        switch (renderType) {
            case NORMAL:
                renders = getNormalRenderBatch(template, struct);
                break;
            default:
                //TODO renderSqlBatch 方法目前仅支持 NORMAL 渲染类型,参数必须是 List<Map<String, Object>> 类型
                log.debug("SYMBOL类型的批量操作请使用 renderSql 并通过 isBatch=false + @SqlParams 实现");
                throw new IllegalArgumentException(
                        "renderSqlBatch() 专用于生成DDL/DCL等多语句脚本,仅支持 NORMAL 渲染类型,参数必须是 List<Map<String, Object>> 类型"
                                + "如需执行批量DML操作,请使用 @SqlGet(isBatch=false) + @SqlParams 注解.");
            
        }
        // 其他设置
        for (SqlParamRender render : renders) {
            render.setCommandType(commandType);
            render.setExecutionType(executionType);
            render.setRenderType(renderType);
        }
        return renders;
    }
    
    /**
     * 获取模板中原始输出sql.
     *
     * @param template sql模板
     * @return 返回渲染后模板内容
     */
    private SqlParamRender getOriginalRender(Template template) {
        SqlParamRender render = new SqlParamRender();
        String sql = template != null ? template.renderToString() : StrUtil.EMPTY;
        render.setSql(sql);
        return render;
    }
    
    /**
     * 获取普通sql. 示例： 1：sql 模板定义 #sql("key") select * from xxx where id = #para(id) #end
     *
     * <p>2：java 代码
     * getSqlPara("key", id);
     *
     * @param template sql模板
     * @param params   参数
     * @return 渲染后模板内容
     */
    private SqlParamRender getNormalRender(Template template, Map<String, Object> params) {
        String sql;
        if (!params.isEmpty()) {
            sql = template.renderToString(params);
        } else {
            sql = template.renderToString();
        }
        
        SqlParamRender render = new SqlParamRender();
        render.setSql(sql);
        return render;
    }
    
    /**
     * 批量执行sql的模板渲染.
     *
     * @param template sql模板
     * @param struct   sql结构参数
     * @return 渲染模板后生成对象
     */
    private List<SqlParamRender> getNormalRenderBatch(Template template, List<Map<String, Object>> struct) {
        List<SqlParamRender> renders = new ArrayList<>();
        for (Map<String, Object> map : struct) {
            String sql = template.renderToString(map);
            SqlParamRender render = new SqlParamRender();
            render.setSql(sql);
            renders.add(render);
        }
        return renders;
    }
    
    /**
     * 获取含参数和占位符的sql. 示例： 1：sql 定义 #sql("key") select * from xxx where id = #p(id) and age > #p(age) #end
     *
     * <p>2：java 代码
     * Kv cond = Kv.by("id", 123).set("age", 18); getParaSymbolSql("key", cond);
     *
     * @param template sql模板
     * @param params   渲染参数集合
     * @return 返回 SqlPara 对象,其中包含 sql和 sql 的有效参数
     */
    private SqlParamRender getSymbolRender(Template template, Map<String, Object> params) {
        SqlParamRender render = new SqlParamRender();
        List<String> paramNames = new ArrayList<>();
        params.put(PlaceHolderDirective.SQL_PARAMS_KEY, paramNames);
        // 渲染sql,并设置
        render.setSql(template.renderToString(params));
        // 使得渲染过程中, 参数名称和参数值一一对应
        List<Object> paramData = new ArrayList<>();
        for (String paramName : paramNames) {
            paramData.add(params.get(paramName));
        }
        // 设置参数
        render.setOriginalParams(params);
        render.setParams(paramData);
        // 避免污染传入的 data
        params.remove(PlaceHolderDirective.SQL_PARAMS_KEY);
        return render;
    }
    
    /**
     * 获取含参数和占位符的sql. 示例： 1：sql 模板定义 #sql("key") select * from xxx where id = #p(id) and age > #p(age) #end
     *
     * <p>2：java 代码
     * Kv cond = Kv.by("id", 123).set("age", 18); getParaSymbolSqlBatch("key", cond);
     *
     * @param template sql模板
     * @param params   批量参数集合
     * @return 渲染模板后生成对象
     */
    private SqlParamRender getSymbolParamsRender(Template template, List<Map<String, Object>> params) {
        SqlParamRender render = new SqlParamRender();
        
        //sql 只需渲染一次就行了, 参数顺序需要重新加载.
        Map<String, Object> firstData = params.get(0);
        List<String> paramNames = new ArrayList<>();
        firstData.put(PlaceHolderDirective.SQL_PARAMS_KEY, paramNames);
        render.setSql(template.renderToString(firstData));
        List<List<Object>> paramList = new ArrayList<>();
        for (Map<String, Object> map : params) {
            List<Object> paramData = new ArrayList<>();
            for (String paramName : paramNames) {
                paramData.add(map.get(paramName));
            }
            paramList.add(paramData);
        }
        render.setOriginalParams(params);
        render.setParams(paramList);
        return render;
    }
    
    /**
     * 通过关键字统计sql模板名个数.
     *
     * @param string string
     * @return 个数
     */
    public int searchSqlTemplateCnt(String string) {
        return sqlTemplateFactory.sqlTemplateCheckCnt(string);
    }
    
    /**
     * 统计所有sql模板个数.
     *
     * @return 个数
     */
    public int searchAllSqlTemplateCnt() {
        return sqlTemplateFactory.sqlTemplateTotalCnt();
    }
}
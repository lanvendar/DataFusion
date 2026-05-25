package com.datafusion.common.template;

import com.datafusion.common.template.ext.directive.DateCalDirective;
import com.datafusion.common.template.ext.directive.NameSpaceDirective;
import com.datafusion.common.template.ext.directive.PlaceHolderDirective;
import com.datafusion.common.template.ext.directive.SqlDirective;
import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * sql模板构建器: 是JFinal Enjoy 从文件模版渲染成sql模板的中间对象.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/04/11
 * @since 2019/12/12
 */
@Slf4j
public class SqlTemplateFactory {
    
    /**
     * 模板文件热加载默认值.
     */
    private final boolean devMode;
    
    /**
     * 模板引擎.
     */
    private final Engine engine;
    
    /**
     * 模板sql文件集合.
     */
    private final List<SqlSource> sqlSourceList = new ArrayList<>();
    
    /**
     * 模板sql文件内存集合.
     */
    private Map<String, SqlTemplate> sqlTemplateMap;
    
    /**
     * 初始化构造.
     *
     * @param configEngineName    自定义模板名称
     * @param devMode             模板文件true热加载,默认false
     */
    public SqlTemplateFactory(String configEngineName, boolean devMode) {
        this.devMode = devMode;
        engine = new Engine(configEngineName);
        engine.setDevMode(devMode);
        //设置加载路径模式为 ClassPathSourceFactory
        engine.setToClassPathSourceFactory();
        //ClassPathSourceFactory 模式下不在需要相对路径,故置空
        engine.setBaseTemplatePath(null);
        //是否开启sql压缩功能
        engine.setCompressorOn(' ');
        //加载扩展函数
        engine.addDirective("namespace", NameSpaceDirective.class);
        engine.addDirective("sql", SqlDirective.class);
        engine.addDirective("day", DateCalDirective.class);
        // #p 指令是参数占位符写法
        engine.addDirective("p", PlaceHolderDirective.class, true);
    }
    
    /**
     * 获取模板.
     *
     * @return engine
     */
    public Engine getEngine() {
        return engine;
    }
    
    /**
     * 获取加载sql模板的路径.
     *
     * @return String Path
     */
    public String getBaseSqlTemplatePath() {
        return engine.getBaseTemplatePath();
    }
    
    /**
     * 单个增加模板.
     *
     * @param sqlSource 模板路径对象
     */
    public void addSqlTemplate(SqlSource sqlSource) {
        sqlSourceList.add(sqlSource);
    }
    
    /**
     * 模板集合对象.
     *
     * @return 模板集合对象
     */
    public Set<Map.Entry<String, SqlTemplate>> getSqlTemplateSet() {
        return sqlTemplateMap.entrySet();
    }
    
    /**
     * 统计模板个数.
     *
     * @return 模板个数.
     */
    public int sqlTemplateTotalCnt() {
        return sqlTemplateMap.size();
    }
    
    /**
     * 统计模板个数.
     *
     * @param string 模板路径 string
     * @return 模板个数.
     */
    public int sqlTemplateCheckCnt(String string) {
        int cnt = 0;
        for (String key : sqlTemplateMap.keySet()) {
            if (key.contains(string)) {
                cnt++;
            }
        }
        return cnt;
    }
    
    /**
     * 从原始文件中渲染解析 .sql 模板,使之按模板中 #namespace.#sql 的形式为 key 存储. sqlTemplateMap 中格式 key = #namespace.#sql value = {@link
     * SqlTemplate} load detail for look {@link SqlDirective}
     */
    public synchronized void renderSqlTemplate() {
        Map<String, SqlTemplate> sqlTemplateMap = new HashMap<>(512, 0.5F);
        for (SqlSource ss : sqlSourceList) {
            Template template = ss.isFile() ? engine.getTemplate(ss.file) : engine.getTemplate(ss.source);
            Map<Object, Object> data = new HashMap<>();
            data.put(SqlDirective.SQL_TEMPLATE_MAP_KEY, sqlTemplateMap);
            template.renderToString(data);
        }
        this.sqlTemplateMap = sqlTemplateMap;
    }
    
    /**
     * 重新加载所有sql模板.
     */
    public void reloadSqlTemplate() {
        //去除 Engine 中的缓存,以免 get 出来后重新判断 isModified
        engine.removeAllTemplateCache();
        log.info("重新加载所有sql模板:");
        renderSqlTemplate();
    }
    
    /**
     * 根据模板中 #namespace.#sql 的 path 获取 SqlTemplate 对象.
     *
     * @param key string path 字符串
     * @return SqlTemplate 模板对象.
     */
    public SqlTemplate getSqlTemplate(String key) {
        SqlTemplate sqlTemplate = sqlTemplateMap.get(key);
        Template template = sqlTemplate.getTemplate();
        if (devMode) {
            if (template == null) {
                synchronized (this) {
                    reloadSqlTemplate();
                    sqlTemplate = sqlTemplateMap.get(key);
                }
            } else {
                if (template.isModified()) {
                    synchronized (this) {
                        reloadSqlTemplate();
                        sqlTemplate = sqlTemplateMap.get(key);
                    }
                } else {
                    return sqlTemplate;
                }
            }
        } else {
            if (template == null) {
                return null;
            } else {
                return sqlTemplate;
            }
        }
        return sqlTemplate;
    }
    
    @Override
    public String toString() {
        return "SqlKit for config : " + engine.getName();
    }
}





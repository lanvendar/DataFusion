package com.datafusion.common.temlate;

import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.template.SqlParamRender;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JFinalSqlBuilderTest {
    @Test
    public void initPathTest() {
        //--------
        JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        test.getSqlTemplateFactory();
        // 发现sql模板文件:[D:\IdeaProjects\datafusion\datafusion-common\target\test-classes\sql\example.sql]
        // 发现sql模板文件:[D:\IdeaProjects\datafusion\datafusion-common\target\test-classes\sql\subPath\test1.sql]
        // 初始化完成,sql模板总数: 12
        //--------
        JFinalSqlBuilder test1 = JFinalSqlBuilder.create()
                .dirPath("D:\\IdeaProjects\\datafusion\\datafusion-common\\src\\test\\resources\\sqlLoadPath")
                .jarPath("D:\\IdeaProjects\\datafusion\\datafusion-common\\src\\test\\resources\\sqlLoadPath\\test-sql.jar")
                .build();
        test1.getSqlTemplateFactory();
        // 发现sql模板文件:[jar:file:/D:/IdeaProjects/datafusion/datafusion-common/src/test/resources/sqlLoadPath/test-sql.jar!/sql/test1.sql]
        // 发现sql模板文件:[D:\IdeaProjects\datafusion\datafusion-common\src\test\resources\sqlLoadPath\test3.sql]
        // 初始化完成,sql模板总数: 14
    }
    
    @Test
    public void simple() {
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        int cnt = test.searchAllSqlTemplateCnt();
        log.info("sql模板数量:" + cnt);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("id", "1234");
        paramMap.put("age", "18");
        paramMap.put("day", "2022-06-23");
        log.info("打印sql模板:" + test.renderSql("test1-key", paramMap).getSql());
    }
    
    @Test
    public void getOriginalSqlTest() {
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        SqlParamRender original = test.renderSql("original", null);
        log.info("原始sql输出:" + original.getSql());
    }
    
    @Test
    public void getNormalSqlTest() {
        //数据准备
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("id", "1234");
        paramMap.put("age", "18");
        paramMap.put("day", "2022-06-23");
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        SqlParamRender normalSql = test.renderSql("normal.key", paramMap);
        log.info("正常sql渲染:" + normalSql.getSql());
        SqlParamRender normalSql2 = test.renderSql("normal.key2", paramMap);
        log.info("正常sql渲染2:" + normalSql2.getSql());
        SqlParamRender normalSql3 = test.renderSql("normal.key3", paramMap);
        log.info("正常sql渲染3:" + normalSql3.getSql());
        SqlParamRender normalSql4 = test.renderSql("normal.key4", paramMap);
        log.info("正常sql渲染4:" + normalSql4.getSql());
    }
    
    @Test
    public void getSymbolSqlAndBatchTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("s_id", "13");
        map.put("s_name", "hadoop");
        map.put("s_birth", "1989-09-10");
        map.put("s_sex", "男");
        map.put("s_create", "2023-10-12");
        map.put("s_update", "2023-10-13");
        Map<String, Object> map1 = new HashMap<>();
        map1.put("s_id", "14");
        map1.put("s_name", "flink");
        map1.put("s_birth", "1989-09-10");
        map1.put("s_sex", "女");
        map1.put("s_create", "2023-10-12");
        map1.put("s_update", "2023-10-12");
        Map<String, Object> map2 = new HashMap<>();
        map2.put("s_id", "15");
        map2.put("s_name", "spark");
        map2.put("s_sex", "男");
        map2.put("s_create", "2023-10-13");
        map2.put("s_update", "2023-10-13");
        map2.put("s_birth", "1989-09-10");
        List<Map<String, Object>> datas = new ArrayList<>();
        datas.add(map);
        datas.add(map1);
        datas.add(map2);
        HashMap<String, Object> rowMap = new HashMap<>();
        rowMap.put("data", datas);
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        SqlParamRender symbolSql = test.renderSql("postgres.batchInsert", map);
        log.info("符号sql:" + symbolSql.getSql());
        log.info("符号sql参数单条:" + symbolSql.getParamsForSingle());
        log.info("符号sql参数多条:" + symbolSql.getExecutionType());
        SqlParamRender symbolSqlBatch = test.renderSql("postgres.batchInsert", rowMap);
        log.info("符号sql:" + symbolSqlBatch.getSql());
        log.info("符号sql参数多条:" + symbolSqlBatch.getParamsForBatch());
        
    }
    
    @Test
    public void joinFuncTest() {
        Map<String, Object> paramMap = new HashMap<>();
        List<String> leftColumns = new ArrayList<>();
        leftColumns.add("leftA");
        leftColumns.add("leftB");
        paramMap.put("leftColumns", leftColumns);
        List<String> rightColumns = new ArrayList<>();
        rightColumns.add("rightA");
        rightColumns.add("rightB");
        paramMap.put("rightColumns", rightColumns);
        paramMap.put("leftTable", "leftTableA");
        paramMap.put("rightTable", "rightTableB");
        List<String> leftTableJoinKey = new ArrayList<>();
        leftTableJoinKey.add("leftJoinKeyA");
        leftTableJoinKey.add("leftJoinKeyB");
        paramMap.put("leftTableJoinKey", leftTableJoinKey);
        List<String> rightTableJoinKey = new ArrayList<>();
        rightTableJoinKey.add("rightJoinKeyA");
        rightTableJoinKey.add("rightJoinKeyB");
        paramMap.put("rightTableJoinKey", rightTableJoinKey);
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        log.info("join sql:" + test.renderSql("join.lookup", paramMap).getSql());
    }
    
    @Test
    public void testSplit() {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("entityIds", "1,2,3");
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        log.info("sql:" + test.renderSql("db1.key", paramMap).getSql());
        
        Map<String, Object> paramMap2 = new HashMap<>();
        List<String> entityIds = new ArrayList<>();
        entityIds.add("1");
        entityIds.add("2");
        entityIds.add("3");
        paramMap2.put("entityIds", entityIds);
        paramMap2.put("id", "1234");
        paramMap2.put("age", "18");
        paramMap2.put("day", "2022-06-23");
        log.info("sql:" + test.renderSql("db1.key2", paramMap2).getSql());
    }
    
    @Test
    public void dynamicSqlBatchTest() {
        //表1
        Params param1 = new Params();
        param1.setTableName("table1");
        param1.setTableDesc("表1");
        List<String> c1 = new ArrayList<>();
        c1.add("c1");
        c1.add("c2");
        c1.add("c3");
        param1.setColumnTypeList(c1);
        List<String> key1 = new ArrayList<>();
        key1.add("id");
        key1.add("c1");
        param1.setPartitionKeyList(key1);
        Map<String, Object> map1 = new HashMap<>();
        map1.put("params", param1);
        //表2
        Params param2 = new Params();
        param2.setTableName("table2");
        param2.setTableDesc("表2");
        List<String> c2 = new ArrayList<>();
        c2.add("b1");
        c2.add("b2");
        c2.add("b3");
        param2.setColumnTypeList(c2);
        List<String> key2 = new ArrayList<>();
        key2.add("id2");
        param2.setPartitionKeyList(key2);
        Map<String, Object> map2 = new HashMap<>();
        map2.put("params", param2);
        List<Map<String, Object>> datas = new ArrayList<>();
        datas.add(map1);
        datas.add(map2);
        final JFinalSqlBuilder test = JFinalSqlBuilder.create().build();
        List<SqlParamRender> renders = test.renderSqlBatch("hive.batchCreateHiveTable", datas);
        for (SqlParamRender render : renders){
            log.info("sql:" + render.getSql());
        }
    }
    
    @Data
    public class Params {
        private String tableName;
        
        private String tableDesc;
        
        private List<String> columnTypeList;
        
        private List<String> partitionKeyList;
    }
}

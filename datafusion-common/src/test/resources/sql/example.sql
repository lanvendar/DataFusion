### 本文件为所有模板的样例文件,后续可维护
### commandType 默认为 select; executionType 默认为 statement; renderType 默认为 normal;

#sql(id= "original",render = "original")
select * from table1 where id = '123' and age > 18 and day = '2020-01-01'
#end

#namespace("normal")
#sql(id="key")
select * from xxx where id = '#(id)' and age > #(age) and day = #day(day,'-2M', 'yyyy-MM-dd', 'MD')
#end

#sql(id="key2")
select * from xxx where id = '#(id)' and age > #(age) and day = #day()
#end

#sql(id="key3")
select * from xxx where id = '#(id)' and age > #(age) and day = #day('2022-06-24', '1D', 'yyyy/MM/dd')
#end

#sql(id="key4")
select * from xxx where id = '#(id)' and age > #(age) and day = #day('20220625', '1D')
#end
#end

#namespace("postgres")
#sql(id = "batchInsert",command = "insert",execution = "statement",render = "symbol")
INSERT INTO student (s_id,s_name,s_birth,s_sex,s_create,s_update) VALUES (#p(s_id),#p(s_name),#p(s_birth),#p(s_sex),#p(s_create),#p(s_update));
#end
#end


#namespace("join")
#sql(id = "lookup")
SELECT
    #for(columnName: leftColumns)
    o.#(columnName),
        #end
            #for(columnName: rightColumns)
        c.#(columnName)
        #if(!for.last)
        ,
        #end
            #end
FROM #(leftTable) AS o
         JOIN #(rightTable) FOR SYSTEM_TIME AS OF o.proc_time AS c
ON
    #for(lKey : leftTableJoinKey)
    o.#(lKey) = c.#(rightTableJoinKey[for.index])
        #if(!for.last)
        and
        #end
    #end
#end
#end


#namespace("db1")
#sql(id="key",command="insert")
select * from aaa where
#if((entityIds) != null && entityIds.length() > 0)
entity_id in(
#for(x : entityIds.split(","))
  #if(for.first)
    '#(x)'
  #else
    ,'#(x)'
  #end
#end
)
#end
#end

#sql(id="key2")
select * from bbb
where
entity_id in(
#for(x : entityIds)
  #if(for.first)
    '#(x)'
  #else
    ,#(x)
  #end
#end
)
and id = '#(id)' and age > #(age) and day = '#day(day,'-2M', 'yyyMMdd','MS')' and long = '#(long)'
and date = #day(day,0,'yyyy/MM/dd','MD')
and date = #day()
and date = #day(day)
#end
#end

### hive没有批量执行,故需要这种写法
#namespace("hive")
#sql(id = "batchInsert",command = "insert",execution = "statement")
#for(row : rows)
INSERT INTO student (s_id,s_name,s_birth,s_sex,s_create,s_update) VALUES (#(row.s_id),'#(row.s_name)','#(row.s_birth)','#(row.s_sex)','#(row.s_create)','#(row.s_update)');
#end
#end

#sql(id = "batchCreateHiveTable",command = "creator",execution = "statement",render = "normal")
#for(param : params)
CREATE TABLE #(param.tableName) (
    #for(columnType : param.columnTypeList)
      #if(for.last)
        #(columnType)
      #else
        #(columnType),
      #end
    #end
)
#if(param.tableDesc != '' && param.tableDesc != null)
    COMMENT #(param.tableDesc)
#end
#if(param.partitionKeyList != null)
    PARTITIONED BY (
    #for(partitionKey : param.partitionKeyList)
      #if(for.last)
        #(partitionKey)
      #else
        #(partitionKey),
      #end
    #end
    )
#end
stored as orc;
#end
#end
#end


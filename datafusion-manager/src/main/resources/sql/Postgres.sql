#namespace("postgres")
#sql(id = "test_connect")
    SELECT 1 AS TEST;
#end

#sql(id = "runSql", command = "create")
    #if(defaultSet != '' && defaultSet != null)
        #(defaultSet)
    #end
    #(runSql);
#end

#sql(id = "metadata")
SELECT
    ns.nspname AS table_schema,
    c.relname AS table_name,
    d_table.description AS table_desc,
    a.attnum AS ordinal_position,
    a.attname AS column_name,
    d_col.description AS column_desc,
    g.typname AS column_type,
    pg_get_expr(ad.adbin, ad.adrelid) AS default_value,
    NOT a.attnotnull AS is_nullable,
    pk.primary_keys as primary_keys,
    (c.relkind = 'v') AS is_view,
    CASE
        WHEN a.atttypmod > 0 AND g.typname IN ('varchar', 'bpchar', 'char', 'text', 'bit', 'varbit') THEN a.atttypmod - 4
        ELSE NULL
        END AS column_length,
    CASE
        WHEN g.typname = 'numeric' AND a.atttypmod > -1 THEN (a.atttypmod - 4) >> 16 & 65535
        ELSE NULL END AS column_precision,
    CASE
        WHEN g.typname = 'numeric' AND a.atttypmod > -1 THEN (a.atttypmod - 4) & 65535
        ELSE NULL
    END AS scale,
    v.definition AS view_def
FROM
    pg_class c
JOIN
    pg_namespace ns ON c.relnamespace = ns.oid
JOIN
    pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
JOIN
    pg_type g ON a.atttypid = g.oid
LEFT JOIN
    pg_description d_table ON d_table.objoid = c.oid AND d_table.objsubid = 0
LEFT JOIN
    pg_description d_col ON d_col.objoid = c.oid AND d_col.objsubid = a.attnum
LEFT JOIN
    pg_attrdef ad ON a.attrelid = ad.adrelid AND a.attnum = ad.adnum
LEFT JOIN
    pg_views v ON ns.nspname = v.schemaname AND c.relname = v.viewname
left join (
    SELECT c.oid,
       string_agg(a.attname, ',' ORDER BY p.ordinality) AS primary_keys
FROM pg_index i
         JOIN
     pg_class c ON c.oid = i.indrelid
         JOIN
     pg_namespace n ON n.oid = c.relnamespace
         JOIN
     pg_attribute a ON a.attrelid = i.indrelid
         JOIN
     unnest(i.indkey) WITH ORDINALITY AS p(attnum, ordinality) ON a.attnum = p.attnum
WHERE i.indisprimary = TRUE
  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
  AND n.nspname NOT LIKE 'pg_toast%'
GROUP BY c.oid
        ) pk on pk.oid = c.oid
WHERE ns.nspname = '#(schemaInfo)' and c.relkind in('r','p','v')
    #if(tableNames != null && tableNames.size() >0 )
       AND c.relname in (
       #for(tableName : tableNames)
           #if(for.first)
           '#(tableName)'
           #else
           ,'#(tableName)'
           #end
       #end
       )
     #end
ORDER BY c.relname, a.attnum;
#end

#sql(id = "countTables")
    SELECT count(*) FROM information_schema.tables WHERE table_schema = '#(schemaInfo)' AND table_type = 'BASE TABLE';
#end

#sql(id = "countByTable")
   SELECT
        CAST(c.reltuples AS bigint) AS estimate_row_count
        FROM
            pg_class c
                JOIN
            pg_namespace n ON n.oid = c.relnamespace
        WHERE
            n.nspname = '#(schemaInfo)'
          AND c.relname = '#(tableName)';
#end


#sql(id = "createTable", command = "create")
CREATE TABLE IF NOT EXISTS #(tableName) (
    #for(columnInfo : columnInfos)
        #if(!for.first),#end
        #(columnInfo.getColumnName()) #(columnInfo.getFullColumnType())
        #if(columnInfo.getDefaultValue() != null)
        DEFAULT #(columnInfo.getDefaultValue())
        #end
        #if(!columnInfo.getIsNullable())
        NOT NULL
        #end
        #if(for.last)
            #if(primaryKeys != null)
                ,
                primary key(#(primaryKeys))
            #end
        #end
    #end
    );

    #if(tableDesc != null && tableDesc != '')
        comment on table #(tableName) is '#(tableDesc)';
    #end

    #for(columnInfo : columnInfos)
        #if(columnInfo.getColumnDesc() != null && columnInfo.getColumnDesc() != '')
            comment on column #(tableName).#(columnInfo.getColumnName()) is '#(columnInfo.getColumnDesc())';
        #end
    #end

#end
#sql(id = "alterTable_addColumn")
ALTER TABLE #(columnInfo.tableName) ADD COLUMN  #(columnInfo.getColumnName()) #(columnInfo.getFullColumnType())
#if(columnInfo.getDefaultValue() != null)
DEFAULT #(columnInfo.getDefaultValue())
#end
#if(!columnInfo.getIsNullable())
NOT NULL
#end;
#if(columnInfo.getColumnDesc() != '' && columnInfo.getColumnDesc() != null)
COMMENT ON COLUMN #(columnInfo.tableName).#(columnInfo.getColumnName()) is '#(columnInfo.getColumnDesc())';
#end
#end
#sql(id = "alterTable_changeColumn")
ALTER TABLE  #(columnInfo.tableName) ALTER COLUMN #(columnInfo.getColumnName()) TYPE #(columnInfo.getFullColumnType()) USING #(columnInfo.getColumnName())::#(columnInfo.getFullColumnType());
#if(columnInfo.getDefaultValue() != null)
ALTER TABLE  #(columnInfo.tableName) ALTER COLUMN #(columnInfo.getColumnName()) SET NOT NULL;
#end
#if(columnInfo.getDefaultValue() != null)
ALTER TABLE  #(columnInfo.tableName) ALTER COLUMN #(columnInfo.getColumnName()) SET DEFAULT #(columnInfo.getDefaultValue());
#end
#if(columnInfo.getColumnDesc() != '' && columnInfo.getColumnDesc() != null)
COMMENT ON COLUMN #(columnInfo.tableName).#(columnInfo.getColumnName()) is '#(columnInfo.getColumnDesc())';
#end
#end
#sql(id = "alterTable_deleteColumn")
ALTER TABLE #(columnInfo.tableName) DROP COLUMN #(columnInfo.getColumnName()) ;
#end

#sql(id = "countSizeByTable")
    SELECT  pg_total_relation_size('#(tableName)') AS size;
#end

#sql(id = "pageList")
    SELECT
       #for(param : selectList)
           #if(for.first)
                #(param.columnName)
           #else
                ,#(param.columnName)
           #end
       #end
    FROM #(tableName)
    #(whereSql)
    #(orderSql)
    limit #(pageSize) offset (#(pageSize) * (#(pageNo) - 1));
#end

#sql(id = "getDataPreview")
    SELECT
    *
    FROM #(condition.tableName)
	    #for(item : condition.queryConditions)
	        #if(!for.first)
                AND
            #end
            #(item)
        #end
	#if(condition.orderConditions != null && condition.orderConditions.size() > 0)
    ORDER BY
        #for(item : condition.orderConditions)
           #(item)
		    #if(!for.last) 
            ,          
            #end
        #end
	#end
    limit #(condition.getLimit());
#end
#end

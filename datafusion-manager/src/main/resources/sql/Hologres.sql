#namespace("hologres")

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
    COALESCE(i.indisprimary, FALSE) AS is_primary,
    (c.relkind IN ('v', 'm')) AS is_view,
    CASE
        WHEN g.typname LIKE '%int%' OR g.typname = 'timestamp' THEN NULL
        WHEN a.atttypmod > 0 AND g.typname IN ('varchar', 'bpchar', 'char', 'text', 'bit', 'varbit') THEN a.atttypmod - 4
        WHEN g.typname IN ('time', 'timetz', 'timestamp', 'timestamptz', 'interval') THEN if(a.atttypmod < 0,NULL,a.atttypmod)
        ELSE NULL
        END AS column_length,
    CASE
        WHEN g.typname = 'numeric' AND a.atttypmod > -1 THEN (a.atttypmod - 4) >> 16 & 65535
        ELSE NULL END AS column_precision,
    CASE
        WHEN g.typname = 'numeric' AND a.atttypmod > -1 THEN (a.atttypmod - 4) & 65535
        ELSE NULL END AS scale,
    v.definition AS view_def,
    case when (c.relkind = 'r' and c.relispartition) then 's' else c.relkind end  AS relkind,
    pk.properties as table_properties,
    pt.partition_keys as partition_keys
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
    pg_views v ON c.relname = v.viewname AND ns.nspname = v.schemaname
LEFT JOIN
    pg_index i ON i.indrelid = c.oid AND i.indisprimary = TRUE AND a.attnum = ANY(i.indkey)
left join (
SELECT table_namespace                                as table_schema,
       table_name,
       jsonb_object_agg(property_key, property_value) AS properties
FROM hologres.hg_table_properties
where table_namespace = '#(schemaInfo)'
  and (property_key in
       ('primary_key', 'distribution_key', 'clustering_key', 'orientation', 'time_to_live_in_seconds', 'storage_format',
        'bitmap_columns', 'dictionary_encoding_columns', 'segment_key') or property_key like '%auto_partitioning%')
GROUP BY table_namespace,
         table_name
) as pk on ns.nspname = pk.table_schema and c.relname = pk.table_name
left join (
select n.nspname                                        AS table_schema,
       c.relname                                        AS table_name,
       string_agg(a.attname, ',' ORDER BY p.ordinality) AS partition_keys
FROM pg_partitioned_table pt
         JOIN
     pg_class c ON c.oid = pt.partrelid
         JOIN
     pg_namespace n ON n.oid = c.relnamespace
         JOIN
     pg_attribute a ON a.attrelid = pt.partrelid
         JOIN
     unnest(pt.partattrs) WITH ORDINALITY AS p(attnum, ordinality) ON a.attnum = p.attnum
WHERE n.nspname = '#(schemaInfo)'
GROUP BY n.nspname,
         c.relname
) as pt on ns.nspname = pt.table_schema and c.relname = pt.table_name
WHERE
    ns.nspname = '#(schemaInfo)'
    AND c.relkind IN ('r', 'v', 'p', 'm', 'f', 's')
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
    SELECT count(*) FROM information_schema.tables WHERE  table_type = 'BASE TABLE';
#end

#sql(id = "getTableRelkind")
    SELECT c.relkind
    FROM pg_class AS c
         INNER JOIN pg_namespace AS ns ON c.relnamespace = ns.oid
    WHERE ns.nspname = '#(schemaName)'
  AND c.relname = '#(tableName)';
#end

#sql(id = "createTable", command = "create")
CREATE TABLE IF NOT EXISTS #(tableName) (
    #for(columnInfo : columnInfos)
        #(columnInfo.getColumnName()) #(columnInfo.getFullColumnType())
        #if(columnInfo.getDefaultValue() != null)
        DEFAULT #(columnInfo.getDefaultValue())
        #end
        #if(!columnInfo.getIsNullable())
        NOT NULL
        #end
        #if(!for.last)
            ,
        #end
        #if(for.last)
            #if(primaryKeys != null)
                ,primary key(#(primaryKeys))
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
ALTER TABLE  #(columnInfo.tableName) ALTER COLUMN #(columnInfo.getColumnName()) TYPE #(columnInfo.getFullColumnType());
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

#sql(id = "countSizeByTable")
SELECT  pg_total_relation_size('#(tableName)') AS size;
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

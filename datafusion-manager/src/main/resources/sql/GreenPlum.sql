#namespace("green_plum")
    #sql(id = "metadata")
    SELECT t.table_schema,
           t.table_name,
           t.table_desc,
           t.ordinal_position,
           t.column_name,
           t.column_desc,
           t.column_type,
           c.column_default,
           t.is_nullable,
           t.is_primary,
           t.is_view,
           (
               CASE
                   WHEN (t.column_type LIKE '%int%' OR t.column_type = 'timestamp') THEN NULL
                   WHEN c.character_maximum_length IS NOT NULL THEN c.character_maximum_length
                   WHEN c.numeric_precision IS NOT NULL THEN c.numeric_precision
                   WHEN c.datetime_precision IS NOT NULL THEN c.datetime_precision
                   ELSE NULL
               END
           ) AS column_length,
           (
               CASE
                   WHEN c.numeric_scale > 0 THEN c.numeric_scale
                   ELSE NULL
               END
           ) AS column_precision,
           v.definition AS view_def
      FROM (SELECT n.nspname AS table_schema,
                   c.relname AS table_name,
                   obj_description (c.oid, 'pg_class') AS table_desc,
                   a.attnum AS ordinal_position,
                   a.attname AS column_name,
                   col_description (c.oid, a.attnum) AS column_desc,
                   t.typname AS column_type,
                   NOT a.attnotnull AS is_nullable,
                   (
                       CASE
                           WHEN pk.conname IS NOT NULL THEN TRUE
                           ELSE FALSE
                       END
                   ) AS is_primary,
                   (
                       CASE
                           WHEN c.relkind = 'r' THEN FALSE
                           WHEN c.relkind = 'v' THEN TRUE
                       END
                   ) AS is_view
              FROM pg_namespace n
              JOIN pg_class c ON n.oid = c.relnamespace
              JOIN pg_attribute a ON c.oid = a.attrelid
              JOIN pg_type t ON a.atttypid = t.oid
              LEFT JOIN pg_constraint pk ON pk.conrelid = c.oid AND a.attnum = ANY (pk.conkey) AND pk.contype = 'p'
             WHERE c.relkind IN ('r', 'v')
               AND c.relname NOT LIKE 'pg_%'
               AND c.relname NOT LIKE 'sql_%'
               AND a.attnum > 0
               AND NOT a.attisdropped
      ) t
      JOIN information_schema.columns c ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.column_name = c.column_name
      LEFT JOIN pg_views AS v ON c.table_name = v.viewname AND c.table_schema = v.schemaname
     WHERE t.table_schema = '#(schemaInfo)'
       #if(tableNames != null && tableNames.size() >0)
       AND c.table_name IN (
           #for(tableName : tableNames)
               #if(for.first)
               '#(tableName)'
               #else
               ,'#(tableName)'
               #end
           #end
       )
       #end
     ORDER BY t.table_name, t.ordinal_position
    #end

    #sql(id = "countTables")
     SELECT count(*) FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE c.relkind IN ('r', 'v') AND n.nspname = '#(schemaInfo)'
    #end

    #sql(id = "countByTable")
        SELECT count(*) as row_count FROM #(tableName)  #(whereSql);
    #end

    #sql(id = "countSizeByTable")
        SELECT
            CONCAT(
                    ROUND(pg_total_relation_size('#(tableName)') / 1024, 2),
                    ' KB'
            ) AS size
        FROM
            pg_tables
        WHERE
            tablename = '#(tableName)'
    #end

    #sql(id = "pageList")
        SELECT
            #for(param : selectList)
                #if(for.first)
                        #(param.columnName)
                    #else
                        , #(param.columnName)
                    #end
                #end
        FROM
            #(tableName)
        #(whereSql)
        #(orderSql)
        LIMIT #(pageSize) OFFSET(#(pageNo) - 1) * #(pageSize);
    #end
#end
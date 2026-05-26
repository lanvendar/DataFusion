#namespace("sqlserver")
    #sql(id = "try_connect")
    SELECT 1 AS test;
    #end

    #sql(id = "metadata")
    SELECT s.name AS table_schema,
           t.name AS table_name,
           cast(ep.value AS varchar) AS table_desc,
           c.column_id AS ordinal_position,
           c.name AS column_name,
           cast(ep2.value AS varchar) AS column_desc,
           t2.name AS column_type,
           REPLACE(REPLACE(dc.definition, '(', ''), ')', '') AS column_default,
           c.is_nullable AS is_nullable,
           cast(
                   CASE
                       WHEN COL_NAME(idx.object_id, idx.column_id) IS NOT NULL THEN 1
                       ELSE 0
                   END AS bit
           ) AS is_primary,
           cast(c.max_length AS int) AS column_length,
           cast(c.[precision] AS int) AS column_precision,
           cast(
                   CASE
                       WHEN t.[type] = 'V' THEN 1
                       ELSE 0
                   END AS bit
           ) AS is_view,
           sm.definition AS view_def
      FROM sys.all_objects t
      LEFT JOIN sys.schemas s ON s.schema_id = t.schema_id
      LEFT JOIN sys.columns c ON c.object_id = t.object_id
      LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'
      LEFT JOIN sys.extended_properties ep2 ON ep2.major_id = c.object_id AND c.column_id = ep2.minor_id AND ep2.name = 'MS_Description'
      LEFT JOIN sys.types t2 ON t2.user_type_id = c.user_type_id
      LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
      LEFT JOIN (SELECT ic.object_id,
                        ic.column_id
                   FROM sys.indexes AS i
                  INNER JOIN sys.index_columns AS ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                  WHERE i.is_primary_key = 1) idx ON idx.object_id = c.object_id AND idx.column_id = c.column_id
      LEFT JOIN sys.sql_modules sm ON sm.object_id = t.object_id
     WHERE t.[type] IN ('U', 'V')
       AND t.is_ms_shipped = 0
       AND s.name = '#(schemaInfo)'
       #if(tableNames != null && tableNames.size() >0)
       AND t.name IN (
           #for(tableName : tableNames)
               #if(for.first)
               '#(tableName)'
               #else
               ,'#(tableName)'
               #end
           #end
       )
       #end
    #end

    #sql(id = "countTables")
    SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '#(schemaInfo)'
    #end

    #sql(id = "countByTable")
    SELECT count(*) as row_count FROM #(tableName) #(whereSql);
    #end

    #sql(id = "countSizeByTable")
        SELECT
            CAST(SUM(size) * 8 / 1024 AS DECIMAL(10, 2)) AS size
        FROM
            sys.tables t
            INNER JOIN
            sys.indexes i ON t.object_id = i.object_id
            INNER JOIN
            sys.partitions p ON i.object_id = p.object_id AND i.index_id = p.index_id
            INNER JOIN
            sys.allocation_units a ON p.partition_id = a.container_id
        WHERE
            t.name = '#(tableName)'
        GROUP BY
            t.name
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
        OFFSET (#(pageNo) - 1) * #(pageSize) ROWS
            FETCH NEXT #(pageSize) ROWS ONLY
    #end
#end
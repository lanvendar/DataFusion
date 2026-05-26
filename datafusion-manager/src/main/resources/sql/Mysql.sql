#namespace("mysql")
    #sql(id = "test_connect")
    SELECT 1;
    #end
    #sql(id = "runSql", command = "create")
    #if(defaultSet != '' && defaultSet != null)
    #(defaultSet)
    #end
    #(runSql);
    #end
    #sql(id = "metadata")
    SELECT t.TABLE_SCHEMA AS table_schema,
           t.TABLE_NAME AS table_name,
           (
               CASE
                   WHEN t.TABLE_TYPE = 'BASE TABLE' THEN FALSE
                   ELSE TRUE
               END
           ) AS is_view,
           t.TABLE_COMMENT AS table_desc,
           c.COLUMN_NAME AS column_name,
           c.DATA_TYPE AS column_type,
           cast(
               CASE
                   WHEN c.DATA_TYPE LIKE '%int%' THEN NULL
                   WHEN c.CHARACTER_MAXIMUM_LENGTH > 0 THEN c.CHARACTER_MAXIMUM_LENGTH
                   WHEN c.NUMERIC_PRECISION > 0 THEN c.NUMERIC_PRECISION
                   ELSE NULL
               END as UNSIGNED
           ) AS column_length,
           cast(
               CASE
                   WHEN c.DATA_TYPE LIKE '%int%' THEN NULL
                   WHEN c.NUMERIC_SCALE >= 0 THEN c.NUMERIC_SCALE
                   ELSE NULL
               END as UNSIGNED
           ) AS column_precision,
           (
               CASE
                   WHEN (c.IS_NULLABLE = 'YES') THEN TRUE
                   ELSE FALSE
               END
           ) AS is_nullable,
           c.COLUMN_DEFAULT AS column_default,
           (
               CASE
                   WHEN c.COLUMN_KEY = 'PRI' THEN TRUE
                   ELSE FALSE
               END
           ) AS is_primary,
           cast(c.ORDINAL_POSITION as UNSIGNED) as ordinal_position,
           c.COLUMN_COMMENT AS column_desc,
           v.VIEW_DEFINITION AS view_def
      FROM information_schema.TABLES t
      LEFT JOIN information_schema.`COLUMNS` c ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
      LEFT JOIN information_schema.VIEWS v ON t.TABLE_SCHEMA = v.TABLE_SCHEMA AND t.TABLE_NAME = v.TABLE_NAME
     WHERE t.TABLE_SCHEMA = '#(schemaInfo)'
          #if(tableNames != null && tableNames.size() >0)
          AND t.TABLE_NAME IN (
          #for(tableName : tableNames)
             #if(for.first)
             '#(tableName)'
             #else
             ,'#(tableName)'
             #end
          #end
          )
       #end
     ORDER BY table_name, ordinal_position
	#end

    #sql(id = "countTables")
        SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '#(schemaInfo)'
    #end


    #sql(id = "countByTable")
     SELECT cast(TABLE_ROWS as UNSIGNED) from information_schema.TABLES WHERE TABLE_SCHEMA ='#(schemaInfo)'AND  TABLE_NAME = '#(tableName)'
    #end

    #sql(id = "countSizeByTable")
        SELECT
            CONCAT(
                    ROUND((IFNULL(data_length,0) + IFNULL(index_length,0)) / 1024, 2),
                    ' KB'
            ) AS size
        FROM
            information_schema.tables
        WHERE
            table_name = '#(tableName)';
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

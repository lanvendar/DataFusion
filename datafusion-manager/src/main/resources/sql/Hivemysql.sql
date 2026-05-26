#namespace("hive_mysql")
	#sql(id = "metadata")
	SELECT t.table_schema,
           t.table_name,
           t.table_desc,
           t.ordinal_position,
           t.column_name,
           t.column_desc,
           t.column_type,
           t.column_length,
           t.column_precision,
           t.is_primary
      FROM (
            SELECT c.table_schema AS table_schema,
                   c.table_name AS table_name,
                   c.table_desc AS table_desc,
                   (
                       CASE
                           WHEN c.is_primary = 1 THEN (
                               SELECT max(cv.INTEGER_IDX)
                                 FROM DBS d
                                 LEFT JOIN TBLS t ON d.DB_ID = t.DB_ID AND t.TBL_TYPE  = 'MANAGED_TABLE'
                                INNER JOIN SDS s ON t.SD_ID = s.SD_ID
                                INNER JOIN COLUMNS_V2 cv ON s.CD_ID = cv.CD_ID
                                WHERE d.NAME = '#(schemaInfo)'
                                  AND t.TBL_NAME = c.table_name
                                  #if(tableNames != null && tableNames.size() >0)
                                  AND t.TBL_NAME IN (
                                      #for(tableName : tableNames)
                                          #if(for.first)
                                          '#(tableName)'
                                          #else
                                          ,'#(tableName)'
                                          #end
                                      #end
                                  )
                                  #end
                                GROUP BY d.Name, t.TBL_NAME) + c.ordinal_position + 1
                           ELSE c.ordinal_position
                       END
                   ) AS ordinal_position,
                   c.column_name AS column_name,
                   c.column_desc AS column_desc,
                   SUBSTRING_INDEX(c.column_type, '(', 1) AS column_type,
                   cast(if(c.column_type REGEXP ',', SUBSTRING_INDEX(SUBSTRING_INDEX(c.column_type, '(', -1), ',', 1),
                        if(c.column_type REGEXP '\\(', SUBSTRING_INDEX(SUBSTRING_INDEX(c.column_type, '(', -1), ')', 1), NULL)) AS SIGNED) AS column_length,
                   cast(if(c.column_type REGEXP ',',
                              SUBSTRING_INDEX(SUBSTRING_INDEX(c.column_type, ',', -1), ')', 1), NULL) AS SIGNED) AS column_precision,
                   c.is_primary AS is_primary
               FROM (SELECT d.NAME AS table_schema,
                            t.TBL_NAME AS table_name,
                            (SELECT PARAM_VALUE
                               FROM TABLE_PARAMS
                              WHERE TBL_ID = t.TBL_ID
                                AND PARAM_KEY = 'comment') AS table_desc,
                            cv.INTEGER_IDX AS ordinal_position,
                            cv.COLUMN_NAME AS column_name,
                            cv.COMMENT AS column_desc,
                            cv.TYPE_NAME AS column_type,
                            0 AS is_primary
                       FROM DBS d
                       LEFT JOIN TBLS t ON d.DB_ID = t.DB_ID AND t.TBL_TYPE  = 'MANAGED_TABLE'
                      INNER JOIN SDS s ON t.SD_ID = s.SD_ID
                      INNER JOIN COLUMNS_V2 cv ON s.CD_ID = cv.CD_ID
                      WHERE d.NAME = '#(schemaInfo)'
                        #if(tableNames != null && tableNames.size() >0)
                        AND t.TBL_NAME IN (
                            #for(tableName : tableNames)
                                #if(for.first)
                                '#(tableName)'
                                #else
                                ,'#(tableName)'
                                #end
                            #end
                        )
                        #end
                      UNION
                     SELECT d.NAME AS table_schema,
                            t.TBL_NAME AS table_name,
                            (SELECT PARAM_VALUE
                               FROM TABLE_PARAMS
                              WHERE TBL_ID = t.TBL_ID
                                AND PARAM_KEY = 'comment') AS table_desc,
                            pk.INTEGER_IDX AS ordinal_position,
                            pk.PKEY_NAME AS column_name,
                            pk.PKEY_COMMENT AS column_desc,
                            pk.PKEY_TYPE AS column_type,
                            1 AS is_primary
                       FROM DBS d
                       LEFT JOIN TBLS t ON d.DB_ID = t.DB_ID AND t.TBL_TYPE  = 'MANAGED_TABLE'
                      INNER JOIN SDS s ON t.SD_ID = s.SD_ID
                      INNER JOIN PARTITION_KEYS pk ON t.TBL_ID = pk.TBL_ID
                      WHERE d.NAME = '#(schemaInfo)'
                        #if(tableNames != null && tableNames.size() >0)
                        AND t.TBL_NAME IN (
                            #for(tableName : tableNames)
                                #if(for.first)
                                '#(tableName)'
                                #else
                                ,'#(tableName)'
                                #end
                            #end
                        )
                        #end
               ) c
      ) t
     ORDER BY t.table_name, t.ordinal_position
	#end

    #sql(id = "countTables")
     SELECT count(*) FROM DBS d
      LEFT JOIN TBLS t ON d.DB_ID = t.DB_ID AND t.TBL_TYPE  = 'MANAGED_TABLE'
     WHERE d.NAME = '#(schemaInfo)'
    #end

    #sql(id = "countByTable")
        SELECT count(*) as row_count FROM #(tableName) #(whereSql);
    #end

    #sql(id = "countSizeByTable")
        SELECT
            CONCAT(
                    ROUND((data_length + index_length) / 1024, 2),
                    ' KB'
            ) AS size
        FROM
            information_schema.tables
        WHERE
            table_name = '#(tableName)';
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
                LIMIT
                (#(pageNo) - 1) * #(pageSize), #(pageSize);
    #end

#end
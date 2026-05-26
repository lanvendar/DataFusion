#namespace("oracle")
    #sql(id = "try_connect")
    SELECT 'a' FROM dual
    #end

	#sql(id = "metadata")
    SELECT t.owner AS table_schema,
           t.table_name,
           tc.comments AS table_desc,
           t.column_id AS ordinal_position,
           t.COLUMN_NAME,
           c.COMMENTS AS column_desc,
           t.DATA_TYPE AS column_type,
           t.DATA_DEFAULT AS column_default,
           (
               CASE
                   WHEN t.NULLABLE = 'Y' THEN 1
                   ELSE 0
               END
           ) AS is_nullable,
           (
               CASE
                   WHEN p.constraint_type = 'P' THEN 1
                   ELSE 0
               END
           ) AS is_primary,
           (
               CASE
                   WHEN t.CHAR_LENGTH > 0 THEN t.CHAR_LENGTH
                   WHEN t.DATA_PRECISION > 0 THEN t.DATA_PRECISION
                   ELSE NULL
               END
           ) AS column_length,
           (
               CASE
                   WHEN t.DATA_SCALE >= 0 THEN t.DATA_SCALE
                   ELSE NULL
               END
           ) AS column_precision,
           (
               CASE
                   WHEN v.VIEW_NAME IS NOT NULL THEN 1
                   ELSE 0
               END
           ) AS is_view,
           (
               CASE
                   WHEN v.TEXT IS NOT NULL THEN v.TEXT
                   ELSE NULL
               END
           ) AS view_def
      FROM all_tab_columns t
      JOIN all_tables al ON t.owner = al.owner AND t.table_name = al.table_name AND al.TEMPORARY = 'N'
      JOIN all_tab_comments tc ON t.owner = tc.owner AND t.table_name = tc.table_name
      JOIN all_col_comments c ON t.owner = c.owner AND t.table_name = c.table_name AND t.column_name = c.column_name
      LEFT JOIN (
          SELECT p.owner,
                 p.constraint_type,
                 p.table_name,
                 pc.column_name
            FROM all_constraints p
            JOIN all_cons_columns pc ON p.constraint_name = pc.constraint_name AND p.owner = pc.owner
           WHERE p.INDEX_NAME IS NOT NULL
      ) p ON t.owner = p.owner AND t.table_name = p.table_name AND t.column_name = p.column_name
      LEFT JOIN all_views v ON v.OWNER = t.OWNER AND v.VIEW_NAME = t.TABLE_NAME
     WHERE t.OWNER = '#(schemaInfo)'
       #if(tableNames != null && tableNames.size() >0)
       AND t.table_name in (
           #for(tableName : tableNames)
               #if(for.first)
               '#(tableName)'
               #else
               ,'#(tableName)'
               #end
           #end
       )
       #end
     ORDER BY t.table_name, t.column_id
    #end

    #sql(id = "countTables")
      SELECT count(*) FROM all_tables WHERE OWNER = '#(schemaInfo)'
    #end

    #sql(id = "countByTable")
    SELECT count(*) as row_count FROM #(tableName) #(whereSql);
    #end

    #sql(id = "countSizeByTable")
        SELECT
            TO_CHAR(DBMS_LOB.GETLENGTH(DBMS_LOB.FILEOPEN('#(tableName)'))) || ' KB' AS size
        FROM
        dual
    #end

    #sql(id = "pageList")
        SELECT *
        FROM (
                 SELECT a.*, ROWNUM rnum
                 FROM (
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
                      ) a
                 WHERE ROWNUM <= (#(pageNo) * #(pageSize))
             )
        WHERE rnum > ((#(pageNo) - 1) * #(pageSize))
    #end
#end

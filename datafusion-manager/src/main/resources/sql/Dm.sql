#namespace("dm")
    #sql(id = "try_connect")
    SELECT 'a' FROM dual
    #end

	#sql(id = "metadata")
	WITH PrimaryKeys AS (
    SELECT 
        pc.owner,
        pc.table_name,
        LISTAGG(pc.column_name, ',') WITHIN GROUP (ORDER BY pc.position) AS table_primary_keys
    FROM 
        all_constraints p
    JOIN 
        all_cons_columns pc ON p.constraint_name = pc.constraint_name AND p.owner = pc.owner
    WHERE 
        p.constraint_type = 'P'
        AND p.owner = '#(schemaInfo)' 
    GROUP BY 
        pc.owner, pc.table_name 
)
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
                   ELSE NULL
               END
           ) AS column_length,
           (
               CASE
                   WHEN t.DATA_PRECISION > 0 THEN t.DATA_PRECISION
                   ELSE NULL
               END
           ) AS column_precision,
           (
               CASE
                   WHEN t.DATA_SCALE >= 0 THEN t.DATA_SCALE
                   ELSE NULL
               END
           ) AS scale,
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
           ) AS view_def,
		   pk.table_primary_keys
      FROM all_tab_columns t
      JOIN all_tables al ON t.owner = al.owner AND t.table_name = al.table_name AND al.TEMPORARY = 'N'
      LEFT JOIN all_tab_comments tc ON t.owner = tc.owner AND t.table_name = tc.table_name
      LEFT JOIN all_col_comments c ON t.owner = c.schema_name AND t.table_name = c.table_name AND t.column_name = c.column_name
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
	  LEFT JOIN PrimaryKeys pk ON t.owner = pk.owner AND t.table_name = pk.table_name
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
    SELECT count(*) as row_count FROM #(tableName);
    #end

    #sql(id = "countSizeByTable")

         SELECT
         BYTES AS size
         FROM
         DBA_SEGMENTS
         WHERE
         SEGMENT_NAME = '#(tableName)'
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
                          FROM #(tableName)
                           #(whereSql)
                           #(orderSql)
                      ) a
                 WHERE ROWNUM <= (#(pageNo) * #(pageSize))
             )
        WHERE rnum > ((#(pageNo) - 1) * #(pageSize))
    #end
	
	#sql(id = "getDataPreview")
        SELECT a.*, ROWNUM rnum
                 FROM (
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
                      ) a
                 WHERE ROWNUM <= #(condition.getLimit())
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
ALTER TABLE  #(columnInfo.tableName) MODIFY #(columnInfo.getColumnName()) #(columnInfo.getFullColumnType());
#if(columnInfo.getDefaultValue() != null)
ALTER TABLE  #(columnInfo.tableName) MODIFY #(columnInfo.getColumnName())  NOT NULL;
#end
#if(columnInfo.getDefaultValue() != null)
ALTER TABLE  #(columnInfo.tableName) MODIFY #(columnInfo.getColumnName())  DEFAULT #(columnInfo.getDefaultValue());
#end
#if(columnInfo.getColumnDesc() != '' && columnInfo.getColumnDesc() != null)
COMMENT ON COLUMN #(columnInfo.tableName).#(columnInfo.getColumnName()) is '#(columnInfo.getColumnDesc())';
#end
#end
#sql(id = "alterTable_deleteColumn")
ALTER TABLE #(columnInfo.tableName) drop #(columnInfo.getColumnName()) ;
#end
    #sql(id = "createTable", command = "create")

            CREATE TABLE IF NOT EXISTS #(tableInfo.tableName)
            (
                #for(columnInfo : columnInfos)
                    #(columnInfo.getColumnName()) #(columnInfo.getFullColumnType())
                    #if(columnInfo.getDefaultValue() != null)
                    DEFAULT #(columnInfo.getDefaultValue())
                    #end
					#if(!columnInfo.getIsNullable())
                        NOT NULL
                    #end
					,
                #end
				CONSTRAINT "#(primary_key_name)" NOT CLUSTER PRIMARY KEY(#(tableInfo.getPrimaryKeys()))
            ) STORAGE(ON "MAIN", CLUSTERBTR) ;
            #if(tableInfo.getTableDesc() != '' && tableInfo.getTableDesc() != null)
               COMMENT ON TABLE #(tableInfo.tableName) IS '#(tableInfo.getTableDesc())';
            #end
            #for(columnInfo : columnInfos)
                COMMENT ON COLUMN #(tableInfo.tableName).#(columnInfo.getColumnName()) is '#(columnInfo.getColumnDesc())';
                #end
    #end
#sql(id = "runSql", command = "create")
#if(defaultSet != '' && defaultSet != null)
#(defaultSet)
#end
#(runSql);
#end
#end

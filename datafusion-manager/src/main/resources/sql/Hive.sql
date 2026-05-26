#namespace("hive")
    #sql(id = "try_connect")
    SELECT 1
    #end

    #sql(id = "createTable", command = "create")
    #for(param : params)
    CREATE TABLE #(param.tableName) (
        #for(columnDef : param.columnDefs)
            #if(for.last)
                #(columnDef)
            #else
                #(columnDef),
            #end
        #end
    )
    #if(param.tableDesc != '' && param.tableDesc != null)
    COMMENT '#(param.tableDesc)'
    #end

    #if(param.partitionDefs != null)
    PARTITIONED BY (
        #for(partitionDef : param.partitionDefs)
            #if(for.last)
                #(partitionDef)
            #else
                #(partitionDef),
            #end
        #end
    )
    #end
    stored as orc;
    #end
    #end

    #sql(id = "countTables")
    SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '#(schemaInfo)'
    #end

    #sql(id = "countByTable")
      SELECT count(*) as row_count FROM #(tableName)  #(whereSql);
    #end
    #sql(id = "countSizeByTable")
       SELECT
            CONCAT(
                    ROUND(SUM(file_size) / 1024, 2),
                    ' KB'
            ) AS size
        FROM
            (SELECT COUNT(*) AS file_size FROM #(tableName) GROUP BY <partition_column>) t
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


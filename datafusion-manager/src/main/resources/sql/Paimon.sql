#namespace("paimon")
    #sql(id = "try_connect")
    SELECT 1
    #end

    #sql(id = "getTables")
    SHOW TABLES
    #end

    #sql(id = "getTableSchemas")
    SELECT * FROM `#(tableName)$schemas`
    #end

    #sql(id = "createTable", command = "create")
    #for(param : params)
    CREATE TABLE `#(param.tableName)`
    (
        #for(col : param.columnDefs)
            #if(for.last)
                #(col)
            #else
                #(col),
            #end
        #end
    )
    COMMENT '#(param.tableDesc)'
    #if(param.partitionKeys != null)
    PARTITIONED BY (#(param.partitionKeys))
    #end
    TBLPROPERTIES (
        #if(param.properties != null)
            #(param.properties)
        #else
            'bucket' = 1,
            'scan.mode' = 'latest-full',
            'num-sorted-run.compaction-trigger' = 1
        #end
    );
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
            (SELECT COUNT(*) AS file_size FROM #(tableName) ) t
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
                (#(pageNo) - 1) * #(pageSize), #(pageSize)
    #end
#end
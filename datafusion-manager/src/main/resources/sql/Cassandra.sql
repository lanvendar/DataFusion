#namespace("cassandra")
    #sql(id = "try_connect", command = "select")
    use #(databaseName);
    #end

    #sql(id = "tables")
    SELECT keyspace_name as table_schema,
           table_name,
           comment as tableDesc,
           compression
      FROM system_schema.tables
     WHERE keyspace_name = '#(schemaInfo)'
      #if(tableNames != null && tableNames.size() >0 )
       AND table_name IN (
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

    #sql(id = "columns")
    SELECT keyspace_name as table_schema,
           table_name,
           column_name,
           type as column_type,
           position as ordinal_position,
           kind
      FROM system_schema.columns
     WHERE keyspace_name = '#(schemaInfo)'
       #if(tableNames != null && tableNames.size() >0 )
       AND table_name IN (
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
    SELECT count(*) FROM system_schema.tables WHERE keyspace_name = '#(schemaInfo)';
    #end


    #sql(id = "countByTable")
    SELECT count(*) as row_count FROM #(tableName)  #(whereSql)  #(whereSql);
    #end

    #sql(id = "countSizeByTable")
    SELECT '0 kb' AS size FROM #(tableName);
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
            #(schemaInfo).#(tableName)
        #(whereSql)
        #(orderSql)
        LIMIT #(pageSize);
    #end
#end
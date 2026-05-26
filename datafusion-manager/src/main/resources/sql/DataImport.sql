#namespace("DataImport")

#sql(id = "rdbCommonBatchInsert", command = "insert", statement = "prepare")
INSERT INTO #(table_name)
    (
      #for(columnName : column_list)
          #if(for.first)
              #(columnName)
          #else
              ,#(columnName)
          #end
      #end
    )
 VALUES
    (
      #for(columnName : column_list)
          #if(for.first)
              ?
          #else
              ,?
          #end
      #end
    )
#end

#sql(id = "pgBatchUpsert", command = "insert", statement = "prepare")
INSERT INTO #(table_name)
    (
      #for(columnName : column_list)
          #if(for.first)
              #(columnName)
          #else
              ,#(columnName)
          #end
      #end
    )
 VALUES
    (
      #for(field : column_list)
          #if(for.first)
              ?
          #else
              ,?
          #end
      #end
    )
 ON CONFLICT
    (
      #for(primary_key : primary_key_list)
          #if(for.first)
              #(primary_key)
          #else
              ,#(primary_key)
          #end
      #end
    )
 DO UPDATE SET
    #for(columnName : column_list)
          #if(for.first)
              #(columnName) = EXCLUDED.#(columnName)
          #else
              ,#(columnName) = EXCLUDED.#(columnName)
          #end
    #end
#end

#sql(id = "mysqlBatchUpsert", command = "insert", statement = "prepare")
INSERT INTO #(table_name)
    (
      #for(columnName : column_list)
          #if(for.first)
              #(columnName)
          #else
              ,#(columnName)
          #end
      #end
    )
 VALUES
    (
      #for(columnName : column_list)
          #if(for.first)
              ?
          #else
              ,?
          #end
      #end
    )
 ON DUPLICATE KEY UPDATE
     #for(columnName : column_list)
           #if(for.first)
               #(columnName) = VALUES(#(columnName))
           #else
               ,#(columnName) = VALUES(#(columnName))
           #end
     #end
#end


#sql(id = "truncateTable",command = "update",statement = "statement")
    TRUNCATE TABLE #(table_name)
#end

#sql(id = "dropTable",command = "update",statement = "statement")
    DROP TABLE IF EXISTS #(table_name)
#end

#sql(id = "mysqlCreateTable",command = "create",statement = "statement")
    CREATE TABLE IF NOT EXISTS #(table_name) (
        #for(field : field_list)
        #(field.fieldName) #(field.dbFieldType)
            #if(!for.last)
               ,
            #end
        #end
        #if(null != primary_key_list && primary_key_list.size() > 0)
            ,
            PRIMARY KEY (
                #for(primary_key : primary_key_list)
                      #if(for.first)
                          #(primary_key)
                      #else
                          ,#(primary_key)
                      #end
                #end
            )
        #end
    )
#end

#sql(id = "pgCreateTable",command = "create",statement = "statement")
    CREATE TABLE IF NOT EXISTS #(table_name) (
        #for(field : field_list)
        #(field.fieldName) #(field.dbFieldType)
            #if(!for.last)
               ,
            #end
        #end
        #if(null != primary_key_list && primary_key_list.size() > 0)
            , CONSTRAINT #(table_name)_pk
            PRIMARY KEY (
                #for(primary_key : primary_key_list)
                      #if(for.first)
                          #(primary_key)
                      #else
                          ,#(primary_key)
                      #end
                #end
            )
        #end

    )
#end

#sql(id = "starrocksCreateTable",command = "create",statement = "statement")
    CREATE TABLE IF NOT EXISTS #(table_name) (
            #for(field : field_list)
            #(field.fieldName) #(field.dbFieldType)
                #if(!for.last)
                   ,
                #end
            #end
        )
        #if(null != primary_key_list && primary_key_list.size() > 0)
            PRIMARY KEY (
                #for(primary_key : primary_key_list)
                      #if(for.first)
                          `#(primary_key)`
                      #else
                          ,`#(primary_key)`
                      #end
                #end
            )
        #end
#end


#end
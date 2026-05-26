#namespace("maxcompute")
    #sql(id = "test_connect")
        SELECT 1 AS TEST;
    #end

    #sql(id = "runSql", command = "create")
    #if(defaultSet != '' && defaultSet != null)
    #(defaultSet)
    #end
    #(runSql);
    #end
    #sql(id = "createTable", command = "create")

            CREATE TABLE IF NOT EXISTS #(tableInfo.tableName)
            (
                #for(columnInfo : columnInfos)
                    `#(columnInfo.getColumnName())` #(columnInfo.getFullColumnType())
                    #if(columnInfo.getColumnDesc() != '' && columnInfo.getColumnDesc() != null)
                        COMMENT '#(columnInfo.getColumnDesc())'
                    #end
                    #if(!for.last) 
                        ,          
                    #end
                #end
            )
            #if(tableInfo.getTableDesc() != '' && tableInfo.getTableDesc() != null)
                COMMENT '#(tableInfo.getTableDesc())'
            #end
            #if(partitionColumnInfos != null && partitionColumnInfos.size() > 0)
                PARTITIONED BY
                (
                    #for(columnInfo : partitionColumnInfos)
                        `#(columnInfo.getColumnName())` #(columnInfo.getColumnType())
                        #if(columnInfo.getColumnDesc() != '' && columnInfo.getColumnDesc() != null)
                            COMMENT '#(columnInfo.getColumnDesc())'
                        #end
                        #if(!for.last) 
                        ,          
                        #end
                    #end
                )
            #end
            #if(tableInfo.getBucketKeys() != null && tableInfo.getBucketKeys().size() > 0)
                CLUSTERED BY
                (
                    #for(bucketKey : tableInfo.getBucketKeys())
                        `#(bucketKey)`
                         #if(!for.last) 
                         ,          
                         #end
                    #end
                )
                #if(tableInfo.getBucketCount() > 0)
                    INTO #(tableInfo.getBucketCount()) BUCKETS
                #end
            #end
            #if(tableInfo.getTableProperties() != null && tableInfo.getTableProperties().containsKey('lifecycle'))
                 LIFECYCLE #(tableInfo.getTableProperties().get('lifecycle'))
            #end
            ;
    #end
	
    #sql(id = "metadata")
    #if(tableNames != null)
    SELECT
        c.table_schema,
        c.table_name,
        t.table_comment AS table_desc,
        c.ordinal_position,
        c.column_name,
        c.column_comment AS column_desc,
        c.data_type,
        c.column_default AS default_value,
        c.is_nullable,
        false AS is_primary,
        CASE
            WHEN t.view_original_text IS NOT NULL AND length(t.view_original_text) > 0 THEN true
            ELSE false
        END AS is_view,
        t.view_original_text AS view_def,
        c.is_partition_key
    FROM
        SYSTEM_CATALOG.INFORMATION_SCHEMA.columns c
    LEFT JOIN
        SYSTEM_CATALOG.INFORMATION_SCHEMA.tables t
    ON
        c.table_catalog = t.table_catalog
        AND c.table_schema = t.table_schema
        AND c.table_name = t.table_name
    WHERE
        c.table_catalog = '#(schemaInfo)'
        #if(tableNames != null)
           AND c.table_name IN (
           #for(tableName : tableNames)
               #(for.first ? "" : ",") '#(tableName)'
           #end
           )
        #end
    ORDER BY
        c.table_name,c.is_partition_key, c.ordinal_position
    #else
    SELECT
        t.table_schema,
        t.table_name,
        t.table_comment AS table_desc,
        1 as ordinal_position,
        'a' as column_name,
        ''  as column_desc,
        'string' as data_type,
        null as default_value,
        false  as is_nullable,
        false AS is_primary,
        CASE
            WHEN t.view_original_text IS NOT NULL AND length(t.view_original_text) > 0 THEN true
            ELSE false
        END AS is_view,
        t.view_original_text AS view_def,
        false as is_partition_key
    FROM
        SYSTEM_CATALOG.INFORMATION_SCHEMA.tables t
    WHERE
        t.table_catalog = '#(schemaInfo)'
        #if(tableNames != null && tableNames.size() > 0)
        AND t.table_name IN (
        #for(tableName : tableNames)
        #(for.first ? "" : ",") '#(tableName)'
        #end
        )
        #end
    #end
    #end
	
    #sql(id = "getDataPreview")
    SELECT
     *
    FROM #(condition.tableName)
  #if(condition.queryConditions != null && condition.queryConditions.size() > 0)
    WHERE
  #end
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
    LIMIT #(condition.getLimit());
    #end

    #sql(id = "alterTable_addColumn")
    ALTER TABLE  #(tableName) ADD COLUMNS (`#(columnName)` #(columnType) #if(columnDesc != '' && columnDesc != null) COMMENT '#(columnDesc)' #end);
    #end

    #sql(id = "alterTable_changeColumn")
    ALTER TABLE  #(tableName)  CHANGE COLUMN `#(oldColumnName)` `#(newColumnName)` #(newColumnType) #if(newColumnDesc != '' && newColumnDesc != null) COMMENT '#(newColumnDesc)' ;#end
    #end	
#end
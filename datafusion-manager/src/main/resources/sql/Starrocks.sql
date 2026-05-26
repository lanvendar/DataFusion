#namespace("starrocks")
    #sql(id = "test_connect")
    SELECT 1;
    #end
    #sql(id = "runSql", command = "create")
    #if(defaultSet != '' && defaultSet != null)
    #(defaultSet)
    #end
    #(runSql);
    #end

    #sql(id = "createTable", command = "create")
    CREATE TABLE IF NOT EXISTS #(tableName) (
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
        #if(primaryKeys != null)
        #(createKey) (#(primaryKeys))
        #end
        #if(tableDesc != '' && tableDesc != null)
        COMMENT '#(tableDesc)'
        #end
        #if(partitionKeys != null)
        PARTITION BY (#(partitionKeys))
        #end
        #if(bucketKeys != null)
        DISTRIBUTED BY hash(#(bucketKeys)) buckets #(bucketNum)
        #end;
    #end

    #sql(id = "metadata")
    select
        t.TABLE_SCHEMA as table_schema ,
        t.TABLE_NAME as table_name,
        case
            when t.TABLE_TYPE like '%TABLE%'
                then false
            else true
            end as is_view,
        t.TABLE_COMMENT as table_desc,
        c.COLUMN_NAME as column_name,
        c.DATA_TYPE as column_type,
        cast(case
                 when c.CHARACTER_MAXIMUM_LENGTH > 0 then c.CHARACTER_MAXIMUM_LENGTH
                 else null
            end as UNSIGNED) as column_length,
        if(c.data_type = 'decimal',c.numeric_precision,null) as column_precision,
        if(c.data_type = 'decimal',c.numeric_scale,null) as scale,
        case
            when (c.IS_NULLABLE = 'YES')
                then true
            else false
            end as is_nullable,
        c.COLUMN_DEFAULT as column_default,
        case
            when c.COLUMN_KEY = 'PRI'
                then true
            else false
            end as is_primary,
        cast(c.ORDINAL_POSITION as UNSIGNED) as ordinal_position,
        c.COLUMN_COMMENT as column_desc,
        v.VIEW_DEFINITION as view_def,
        p.partition_key as partition_key,
        p.distribute_key as bucket_key,
        p.properties as properties,
        p.distribute_bucket as distribute_bucket,
        p.table_model as table_model,
        case when p.table_model in('DUP_KEYS','AGG_KEYS') then p.sort_key
             else p.primary_key end as primary_key,
        p.sort_key as sort_key

    from
        information_schema.TABLES t
            left join information_schema.`COLUMNS` c on
            t.TABLE_SCHEMA = c.TABLE_SCHEMA
                and t.TABLE_NAME = c.TABLE_NAME
            left join information_schema.VIEWS v on
            t.TABLE_SCHEMA = v.TABLE_SCHEMA
                and t.TABLE_NAME = v.TABLE_NAME
            left join information_schema.tables_config p on
            t.TABLE_SCHEMA = p.TABLE_SCHEMA
                and t.TABLE_NAME = p.TABLE_NAME
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
    order by table_name,ordinal_position
	#end

  #sql(id = "countTables")
      SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '#(schemaInfo)'
  #end


  #sql(id = "countByTable")
  select table_rows  from information_schema.tables where table_schema ='#(schemaInfo)' and  table_name = '#(tableName)'
  #end

  #sql(id = "countSizeByTable")
      SELECT
              ifnull(data_length,0) + ifnull(index_length,0) as size
      FROM
          information_schema.tables
      WHERE
          table_schema ='#(schemaInfo)' and table_name = '#(tableName)';
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

  #sql(id = "showBackends")
      show backends;
  #end

  #sql(id = "showTables")
      show tables from #(databaseName);
  #end

   #sql(id = "countByDayPt")
      select count(*) from #(databaseName).#(tableName) where day_pt = '#(dayPt)'
   #end

   #sql(id = "showData")
      show data from #(databaseName).#(tableName)
   #end

   #sql(id = "alterTable_addColumn")
   ALTER TABLE #(columnInfo.tableName) ADD COLUMN #(columnInfo.columnName) #(columnInfo.fullColumnType) #if(columnInfo.columnDesc != '' && columnInfo.columnDesc != null) COMMENT '#(columnInfo.columnDesc)' #end;
   #end

   #sql(id = "alterTable_changeColumn")
   ALTER TABLE #(columnInfo.tableName) CHANGE COLUMN #(columnInfo.oldColumnName) #(columnInfo.newColumnName) #(columnInfo.newColumnType) #if(columnInfo.newColumnDesc != '' && columnInfo.newColumnDesc != null) COMMENT '#(columnInfo.newColumnDesc)' #end;
   #end
#end

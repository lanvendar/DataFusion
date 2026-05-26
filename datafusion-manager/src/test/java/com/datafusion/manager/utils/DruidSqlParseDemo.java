package com.datafusion.manager.utils;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/22
 * @since 2025/10/22
 */
public class DruidSqlParseDemo {
    
    @Test
    public void DruidTest1() {
        String sql = "create table tmp_query_sebu1_dwd_cec_device_day_data_001_1 as\n" +
                "    select\n" +
                "     t1.node_id\n" +
                "    ,t1.interface_type\n" +
                "    ,t1.id_order\n" +
                "    ,t1.sn\n" +
                "    ,t1.gate\n" +
                "    ,t1.product\n" +
                "    ,t1.tp_order_start\n" +
                "    ,t1.tp_order_end\n" +
                "    ,t1.date_order_start\n" +
                "    ,t1.date_order_end\n" +
                "    ,t1.time_order_start\n" +
                "    ,t1.time_order_end\n" +
                "    ,case when t1.date_order_start = t1.date_field  then t1.tp_order_start\n" +
                "          else UNIX_TIMESTAMP(t1.date_field,'yyyy-MM-dd')*1000 end as final_tp_order_start  --CAST(t1.date_field AS TIMESTAMP)\n" +
                "    ,case when t1.date_order_end= t1.date_field  then t1.tp_order_end\n" +
                "          else UNIX_TIMESTAMP(DATE_ADD(t1.date_field, 1),'yyyy-MM-dd')*1000  end as final_tp_order_end --CAST(t1.date_field AS TIMESTAMP) + INTERVAL '1' DAY\n" +
                "    ,t1.date_field as action_date\n" +
                "    ,t1.min_action_date\n" +
                "    ,if(t1.date_order_end = t1.date_field,1,0) as charge_count_cnt\n" +
                "    ,t1.ep_order\n" +
                "    ,t1.money_elec\n" +
                "    ,t1.money_service\n" +
                "    ,t1.money_total\n" +
                "    ,t1.distributed_code\n" +
                "    ,t1.distz\n" +
                "    from (\n" +
                "          select/*+mapjoin(t2)*/ t1.* ,t2.date as date_field\n" +
                "          from (\n" +
                "                select\n" +
                "                t1.node_id\n" +
                "                 ,t1.interface_type\n" +
                "                 ,t1.id_order\n" +
                "                 ,t1.sn\n" +
                "                 ,t1.gate\n" +
                "                 ,t1.product\n" +
                "                 ,t1.tp_order_start\n" +
                "                 ,t1.tp_order_end\n" +
                "                 ,to_date(from_unixtime(cast(t1.tp_order_end / 1000 as bigint))) as action_date\n" +
                "                 ,to_date(from_unixtime(cast(t1.tp_order_start / 1000 as bigint))) as date_order_start\n" +
                "                 ,to_date(from_unixtime(cast(t1.tp_order_end / 1000 as bigint))) as date_order_end\n" +
                "                 ,cast(from_unixtime(cast(t1.tp_order_start / 1000 as bigint)) as timestamp) as time_order_start\n" +
                "                 ,cast(from_unixtime(cast(t1.tp_order_end / 1000 as bigint)) as timestamp) as time_order_end\n" +
                "                 ,MIN(from_unixtime(cast(t1.tp_order_start / 1000 as bigint))) OVER (PARTITION BY t1.node_id, t1.sn) AS min_action_date\n" +
                "                 ,t1.ep_order\n" +
                "                 ,t1.money_elec\n" +
                "                 ,t1.money_service\n" +
                "                 ,t1.money_total\n" +
                "                 ,t1.distributed_code\n" +
                "                 ,t1.distz\n" +
                "              from sebu1_dwd_cec_order_detail t1 where distz='8' and to_date(from_unixtime(cast(t1.tp_order_end / 1000 as bigint))) <current_date()\n" +
                "          )t1\n" +
                "         join sebu1_dim_date t2 on t2.date between t1.date_order_start and t1.date_order_end\n" +
                ") t1";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.HIVE);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void insertIntoPartition() {
        String sql = "insert into table sebu1_dwd_curve_node_year_monitor_attr partition(distz,partition_date)\n" +
                "select  t1.action_year\n" +
                ", t1.topology_thing_id\n" +
                ", t1.topology_thing_code\n" +
                ", t1.topology_thing_name\n" +
                ", t1.node_id\n" +
                ", t1.node_type_id\n" +
                ", t1.node_type_name\n" +
                ", t1.tag\n" +
                ", t1.value\n" +
                ", t1.etl_update_date\n" +
                ", t1.etl_update_dt\n" +
                ", t1.distz\n" +
                ", concat(t1.action_year,'-01-01') as partition_date\n" +
                "from tmp.tmp_query_sebu1_dwd_curve_node_year_monitor_attr_20_1 t1\n" +
                "where distz = '8'\n";
    
        //<EdgeTableColumnDto> hive = DruidSqlParseEdgeHandler.parseEdges(sql, "hive");
        //System.out.println(hive);
        /*List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.HIVE);
        System.out.println(sqlStatements);*/
    }
    
    @Test
    public void insertOverwritePartition() {
        String sql = "insert into table dw.sebu1_dwd_curve_node_year_monitor_attr partition(distz,partition_date)\n" +
                "select  t1.action_year\n" +
                ", t1.topology_thing_id\n" +
                ", t1.topology_thing_code\n" +
                ", t1.topology_thing_name\n" +
                ", t1.node_id\n" +
                ", t1.node_type_id\n" +
                ", t1.node_type_name\n" +
                ", t1.tag\n" +
                ", t1.value\n" +
                ", t1.etl_update_date\n" +
                ", t1.etl_update_dt\n" +
                ", t1.distz\n" +
                ", concat(t1.action_year,'-01-01') as partition_date\n" +
                "from tmp_query_sebu1_dwd_curve_node_year_monitor_attr_20_1 t1\n" +
                "where distz = '8'\n";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.ODPS);
        if (sqlStatements.size() == 1) {
            SQLStatement sqlStatement = sqlStatements.get(0);
            //判断是否是insert语句
            if (sqlStatement instanceof SQLInsertStatement) {
                SQLInsertStatement insertStatement = (SQLInsertStatement) sqlStatement;
                //insert overwrite 以及包含partition的需要进行转换
                if (insertStatement.isOverwrite() || CollectionUtils.isNotEmpty(insertStatement.getPartitions())) {
                    //获取目标表名
                    String targetTableName = insertStatement.getTableName().toString();
                    if (insertStatement.getQuery() != null) {
                        //sb.append(" ").append(SQLUtils.toSQLString(insertStatement.getQuery(), JdbcConstants.HIVE));
                    }
                }
                //
                insertStatement.getTableName().toString();
                System.out.println(insertStatement);
                //SQLSelect query = insertStatement.getQuery().to;
            }
        }
        
        System.out.println(sqlStatements);
    }
    
    @Test
    public void testAlterTable() {
        String sql = "ALTER TABLE sebu1_dwd_curve_node_year_monitor_attr DROP IF EXISTS PARTITION (partition_date='2024')";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.HIVE);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void test22() {
        String sql = "select t1.date from tmp_query_sebu1_dwd_node_month_time_sharing_incr_agg_02_02_8l t1";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.HIVE);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void testMutiSql() {
        String sql = "set odps.sql.submit.mode = script;\n" +
                "set odps.sql.hive.compatible = true;\n" +
                "set odps.optimizer.auto.map.parallelism.mode=normal;\n" +
                "set odps.sql.decimal.odps2 = true;\n" +
                "drop table if exists tmp_query_sebu1_dwd_cec_device_day_data_01_8;\n" +
                "create table tmp_query_sebu1_dwd_cec_device_day_data_01_8 as\n" +
                "  select\n" +
                "   t1.node_id\n" +
                "  ,t1.interface_type\n" +
                "  ,t1.id_order\n" +
                "  ,t1.sn\n" +
                "  ,t1.gate\n" +
                "  ,t1.product\n" +
                "  ,t1.tp_order_start\n" +
                "  ,t1.ep_order\n" +
                "  ,t1.money_elec\n" +
                "  ,t1.money_service\n" +
                "  ,t1.money_total\n" +
                "  ,t1.tp_order_end\n" +
                "  ,to_date(from_unixtime(cast(t1.tp_order_end / 1000 as bigint))) as action_date\n" +
                "  ,t1.distz\n" +
                "  ,t1.distributed_code\n" +
                "  from sebu1_dwd_cec_order_detail t1\n" +
                "  where  distz='8'\n" +
                ";";
        
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.HIVE);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void testHoloSql() {
        String sql = "BEGIN ;\n" +
                "DROP TABLE IF EXISTS dw.tmp_sebu1_ads_manual_sys_month_data;\n" +
                "CALL HG_CREATE_TABLE_LIKE ('dw.tmp_sebu1_ads_manual_sys_month_data', 'select * from dw.sebu1_ads_manual_sys_month_data');\n" +
                "COMMIT ;\n" +
                "\n" +
                "INSERT INTO dw.tmp_sebu1_ads_manual_sys_month_data\n" +
                "SELECT *\n" +
                "FROM tmp.tmp_sebu1_ads_manual_sys_month_data_03;\n" +
                "\n" +
                "BEGIN ;\n" +
                "DROP TABLE IF EXISTS dw.sebu1_ads_manual_sys_month_data;\n" +
                "ALTER TABLE IF EXISTS dw.tmp_sebu1_ads_manual_sys_month_data RENAME TO sebu1_ads_manual_sys_month_data;\n" +
                "COMMIT ;";
        
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.POSTGRESQL);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void testCreateWith() {
        String sql = "create table dw.tmp_sebu1_device_flip_p_correct as\n" +
                "with tmp_sebu1_dim_device_flip_p as (\n" +
                "   select t1.sn\n" +
                "   , case when (t3.node_type !='Grid' or t3.node_type is null) then '0' else t1.is_valid end as is_valid\n" +
                "   from dw.sebu1_dim_device_flip_p t1\n" +
                "   left join ( select topology_node_id as node_id,thing_code as sn from dw.sebu1_ods_secp_sniper_topology_node_thing_rel ) t2\n" +
                "   on t1.sn=t2.sn\n" +
                "   left join ( select id as node_id,node_type from dw.sebu1_ods_secp_sniper_topology_node_info ) t3\n" +
                "   on t2.node_id=t3.node_id\n" +
                "   where t1.device_template_code='ElectricMeter' and is_valid='1' and operator='system'\n" +
                "),\n" +
                "tmp_charger_gun as (\n" +
                "    select code as sn\n" +
                "    ,'1' as is_valid\n" +
                "    ,template_code as device_template_code\n" +
                "    ,'system' as operator\n" +
                "    from dw.sebu1_ods_secp_sniper_thing_info\n" +
                "    where template_code ='charger_gun'\n" +
                ")\n" +
                "select sn,is_valid,'ElectricMeter' as device_template_code,'system' as operator from tmp_sebu1_dim_device_flip_p where is_valid = '0' group by sn,is_valid\n" +
                "union\n" +
                "select sn,is_valid,device_template_code,operator from tmp_charger_gun;";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.POSTGRESQL);
        System.out.println(sqlStatements);
    }
    
    @Test
    public void testGetTable() {
        
        String sql =
                "insert into tmp_query_sebu1_dws_device_day_data_05_01_8l SELECT t1.action_date, CAST(date_format(t1.action_date, 'yyyy-mm-01') AS DATE) AS action_month, CAST(date_format(t1.action_date, 'yyyy-01-01') AS DATE) AS action_year, t5.topology_thing_id, t6.topology_thing_code\n" +
                        "\t, t6.topology_thing_name, t4.node_id AS node_id, t5.node_type_id, t5.node_type_name, t1.sn\n" +
                        "\t, t4.device_name AS sn_name, t2.cver, t2.flip, t2.e_first_rd_action_time, t2.e_first_rd\n" +
                        "\t, t2.e_flat_first_rd_action_time, t2.e_flat_first_rd, t2.e_peak_first_rd_action_time, t2.e_peak_first_rd, t2.e_critical_first_rd_action_time\n" +
                        "\t, t2.e_critical_first_rd, t2.e_valley_first_rd_action_time, t2.e_valley_first_rd, t2.ep_first_rd_action_time, t2.ep_first_rd\n" +
                        "\t, t2.ep_flat_first_rd_action_time, t2.ep_flat_first_rd, t2.ep_peak_first_rd_action_time, t2.ep_peak_first_rd, t2.ep_critical_first_rd_action_time\n" +
                        "\t, t2.ep_critical_first_rd, t2.ep_valley_first_rd_action_time, t2.ep_valley_first_rd, t2.e_last_rd_action_time, t2.e_last_rd\n" +
                        "\t, t2.e_flat_last_rd_action_time, t2.e_flat_last_rd, t2.e_peak_last_rd_action_time, t2.e_peak_last_rd, t2.e_critical_last_rd_action_time\n" +
                        "\t, t2.e_critical_last_rd, t2.e_valley_last_rd_action_time, t2.e_valley_last_rd, t2.ep_last_rd_action_time, t2.ep_last_rd\n" +
                        "\t, t2.ep_flat_last_rd_action_time, t2.ep_flat_last_rd, t2.ep_peak_last_rd_action_time, t2.ep_peak_last_rd, t2.ep_critical_last_rd_action_time\n" +
                        "\t, t2.ep_critical_last_rd, t2.ep_valley_last_rd_action_time, t2.ep_valley_last_rd, t2.e_incr, t2.e_flat_incr\n" +
                        "\t, t2.e_peak_incr, t2.e_critical_incr, t2.e_valley_incr, t2.ep_incr, t2.ep_flat_incr\n" +
                        "\t, t2.ep_peak_incr, t2.ep_critical_incr, t2.ep_valley_incr, t3.equivalent_gen_hours, t3.equivalent_gen_hours_last_30day\n" +
                        "\t, t3.equivalent_gen_hours_last_30day_vpp, t3.e_incr_last_30day, t3.e_incr_last_30day_vpp, current_date() AS etl_update_date, current_timestamp() AS etl_update_dt\n" +
                        "\t, t1.distributed_code, t1.distz, t2.e_deep_first_rd_action_time, t2.e_deep_first_rd, t2.e_deep_last_rd_action_time\n" +
                        "\t, t2.e_deep_last_rd, t2.e_deep_incr, t2.ep_deep_first_rd_action_time, t2.ep_deep_first_rd, t2.ep_deep_last_rd_action_time\n" +
                        "\t, t2.ep_deep_last_rd, t2.ep_deep_incr\n" +
                        "FROM (\n" +
                        "\tSELECT t1.sn, t1.distz, t1.distributed_code, t1.action_date\n" +
                        "\tFROM tmp.tmp_query_sebu1_dws_device_day_data_01_02_8l t1\n" +
                        "\tUNION\n" +
                        "\tSELECT t1.sn, t1.distz, t1.distributed_code, t1.action_date\n" +
                        "\tFROM tmp_query_sebu1_dws_device_day_data_01_01_8l t1\n" +
                        "\tGROUP BY t1.sn, \n" +
                        "\t\tt1.distz, \n" +
                        "\t\tt1.distributed_code, \n" +
                        "\t\tt1.action_date\n" +
                        ") t1\n" +
                        "LEFT OUTER JOIN tmp_query_sebu1_dws_device_day_data_01_01_8l t2\n" +
                        "ON t1.sn = t2.sn\n" +
                        "\tAND t1.distz = t2.distz\n" +
                        "\tAND t1.distributed_code = t2.distributed_code\n" +
                        "\tAND t1.action_date = t2.action_date\n" +
                        "LEFT OUTER JOIN tmp_query_sebu1_dws_device_day_data_04_01_8l t3\n" +
                        "ON t1.sn = t3.sn\n" +
                        "\tAND t1.distz = t3.distz\n" +
                        "\tAND t1.distributed_code = t3.distributed_code\n" +
                        "\tAND t1.action_date = t3.action_date\n" +
                        "LEFT OUTER JOIN sebu1_dim_device t4\n" +
                        "ON t1.sn = t4.sn\n" +
                        "\tAND t1.distz = t4.distz\n" +
                        "\tAND t1.distributed_code = t4.distributed_code\n" +
                        "JOIN sebu1_dim_node t5\n" +
                        "ON t4.node_id = t5.node_id\n" +
                        "\tAND t1.distz = t5.distz\n" +
                        "\tAND t1.distributed_code = t5.distributed_code\n" +
                        "LEFT OUTER JOIN sebu1_dim_station t6\n" +
                        "ON t5.topology_thing_id = t6.topology_thing_id\n" +
                        "\tAND t1.distz = t6.distz\n" +
                        "\tAND t1.distributed_code = t6.distributed_code";
        //List<String> tables = SQLParserUtils.getTables(sql, JdbcConstants.POSTGRESQL);
        //System.out.println(tables);
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.POSTGRESQL);
        PGSchemaStatVisitor visitor = new PGSchemaStatVisitor();
        sqlStatements.get(0).accept(visitor);
        Map<TableStat.Name, TableStat> tables2 = visitor.getTables();
        Set<String> tables = new HashSet<>();
        for (TableStat.Name name : tables2.keySet()) {
            tables.add(name.getName());
        }
        System.out.println("");
    }
    
    @Test
    public void testRex2() throws SqlParseException {
        String sql = "create table tmp_query_sebu1_dim_node_backup_his_03_${data_distz} as\n" +
                "select t21.*\n" +
                "from (\n" +
                "    select t1.main_node_id\n" +
                "    , tmp.rely_node_id\n" +
                "    , t1.factor\n" +
                "    , t1.backup_type_id\n" +
                "    , t1.priority\n" +
                "    , t1.is_deleted\n" +
                "    , t1.creator\n" +
                "    , t1.updater\n" +
                "    , t1.create_time\n" +
                "    , t1.update_time\n" +
                "    , t1.distributed_code\n" +
                "    , t1.ds partition_date\n" +
                "    , t1.distz\n" +
                "    , row_number()over (partition by t1.distributed_code, t1.main_node_id, tmp.rely_node_id, t1.backup_type_id, t1.priority, t1.is_deleted, t1.ds, t1.distz order by update_time desc) rn\n" +
                "    from (\n" +
                "        select node_id main_node_id\n" +
                "        , split(rely_node_set, ',') rely_node_arry\n" +
                "        , 1 factor\n" +
                "        , '2' backup_type_id\n" +
                "        , 1 priority\n" +
                "        , cast(if_del as bigint) is_deleted\n" +
                "        , creator\n" +
                "        , updater\n" +
                "        , cast(create_time as timestamp) create_time\n" +
                "        , cast(update_time as timestamp) update_time\n" +
                "        , distributed_code\n" +
                "        , ds\n" +
                "        , distz\n" +
                "        from sebu1_ods_secp_dw_node_tag_up_backup_config\n" +
                "        where ds >= '${BASE_DATE}' and distz = '${BASE_DISTZ}') t1\n" +
                "    lateral view explode(t1.rely_node_arry) tmp as rely_node_id\n" +
                ") t21\n" +
                "left anti join (\n" +
                "    select t1.main_node_id\n" +
                "    , partition_date\n" +
                "    , distz\n" +
                "    , distributed_code\n" +
                "    from tmp_query_sebu1_dim_node_backup_his_01_${data_distz} t1\n" +
                "    where t1.rn = 1\n" +
                "    group by t1.main_node_id, partition_date, distz, t1.distributed_code\n" +
                ") t22 on t21.main_node_id = t22.main_node_id and t21.partition_date = t22.partition_date and t21.distz = t22.distz and t21.distributed_code = t22.distributed_code\n" +
                "where t21.rn = 1";
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.ODPS);
        System.out.println("...........");
        
    }
}

package com.datafusion.common.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 数据库类型枚举.
 *
 * @author lanvendar
 * @version 1.0.0, 2021/12/30
 * @since 2021/12/30
 */
public enum DatabaseTypeEnum {
    
    /**
     * mysql数据库.
     * 驱动:mysql-5.x 驱动类:com.mysql.jdbc.Driver. mysql-8.x 驱动类:com.mysql.cj.jdbc.Driver.
     */
    MYSQL("MySQL", MySQLDriver.V8),
    /**
     * postgres数据库.
     */
    POSTGRES("Postgres", PostgresDriver.DEFAULT),
    /**
     * oracle数据库.
     */
    ORACLE("Oracle", OracleDriver.DEFAULT),
    /**
     * sqlserver数据库.
     */
    SQLSERVER("SQL Server", SqlServerDriver.DEFAULT),
    /**
     * 金仓数据库.
     */
    KINGBASE("kingbase", KingbaseDriver.DEFAULT),
    /**
     * 达梦.
     */
    DM("dm", DmDriver.DEFAULT),
    /**
     * cassandra数据库.
     */
    CASSANDRA("Cassandra", null),
    /**
     * hive数据库.
     */
    HIVE("Hive", HiveDriver.DEFAULT),
    /**
     * 阿里DLF数据源.
     */
    DLF("Data Lake Formation", null),
    /**
     * csv、excel 数据.
     */
    EXCEL("Excel", null),
    
    /**
     * greenplum 数据库.
     */
    GREENPLUM("Greenplum", null),
    
    /**
     * paimon数据源.
     */
    PAIMON("Paimon", null),
    
    /**
     * clickhouse 数据库.
     */
    CLICKHOUSE("ClickHouse", ClickHouseDriver.DEFAULT),
    
    /**
     * starrocks 数据库.
     */
    STARROCKS("StarRocks", MySQLDriver.V8),
    /**
     * maxcompute 数据库.
     */
    MAXCOMPUTE("MaxCompute", MaxcomputeDriver.DEFAULT),

    /**
     * hologres 数据库.
     */
    HOLOGRES("Hologres", PostgresDriver.DEFAULT);
    
    /**
     * 数据库标准名称.
     */
    private final String typeName;
    
    /**
     * 数据库默认驱动.
     */
    private final Driver defaultDriver;
    
    /**
     * 大数据库database枚举集合.
     */
    public static final Set<DatabaseTypeEnum> BIGDATA_DATABASE = EnumSet.of(
            DatabaseTypeEnum.STARROCKS,
            DatabaseTypeEnum.HOLOGRES,
            DatabaseTypeEnum.MAXCOMPUTE
    );
    
    /**
     * 关系型库database枚举集合.
     */
    public static final Set<DatabaseTypeEnum> RELATIONAL_DATABASE = EnumSet.of(
            DatabaseTypeEnum.POSTGRES,
            DatabaseTypeEnum.MYSQL,
            DatabaseTypeEnum.DM
    );
    
    /**
     * 构造方法.
     *
     * @param typeName      数据库类型
     * @param defaultDriver 默认驱动
     */
    DatabaseTypeEnum(String typeName, Driver defaultDriver) {
        this.typeName = typeName;
        this.defaultDriver = defaultDriver;
    }


    /**
     * 判断数据库类型是否包含在枚举中.
     *
     * @param databaseType 数据库类型
     * @return 返回判断结果
     */
    public static boolean contains(String databaseType) {

        return Arrays.stream(DatabaseTypeEnum.values()).anyMatch(s -> s.name().equalsIgnoreCase(databaseType));
    }

    /**
     * 获取数据库类型标准名称,枚举名称可使用 {@link #getType()}.
     *
     * @return 返回数据库类型名称
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * 获取数据库类型.
     *
     * @return 获取数据库类型
     */
    public String getType() {
        return this.name().toLowerCase();
    }
    
    /**
     * 字符串转化枚举类型.
     *
     * @param databaseType 目标字符串
     * @return 返回枚举
     */
    public static DatabaseTypeEnum fromString(String databaseType) {
        if (databaseType == null) {
            return null;
        }
        return Arrays.stream(DatabaseTypeEnum.values()).filter(s -> s.name().toLowerCase().equals(databaseType))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid StatementTypeEnum: " + databaseType));
    }
    
    @Override
    public String toString() {
        return this.typeName;
    }
    
    //region 获取数据库驱动
    
    /**
     * 定义所有驱动必须实现的通用接口.
     */
    public interface Driver {
        /**
         * 获取驱动类名.
         *
         * @return 返回驱动类名
         */
        String getDriverClassName();
    }
    
    /**
     * MySQL 驱动版本.
     */
    public enum MySQLDriver implements Driver {
        /**
         * MySQL 5.x.
         */
        V5("com.mysql.jdbc.Driver"),
        /**
         * MySQL 8.x.
         */
        V8("com.mysql.cj.jdbc.Driver");
        
        /**
         * MySQL 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param name 驱动类名
         */
        MySQLDriver(String name) {
            this.className = name;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum PostgresDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("org.postgresql.Driver");
        
        /**
         * Postgres 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        PostgresDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum OracleDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("oracle.jdbc.driver.OracleDriver");
        
        /**
         * Oracle 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        OracleDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum SqlServerDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        
        /**
         * SqlServer 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        SqlServerDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum KingbaseDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("com.kingbase8.Driver");
        
        /**
         * Kingbase 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        KingbaseDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum DmDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("dm.jdbc.driver.DmDriver");
        
        /**
         * Dm 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        DmDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum HiveDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("org.apache.hive.jdbc.HiveDriver");
        
        /**
         * Hive 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        HiveDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    
    public enum ClickHouseDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("ru.yandex.clickhouse.ClickHouseDriver");
        
        /**
         * ClickHouse 驱动类名.
         */
        private final String className;
        
        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        ClickHouseDriver(String className) {
            this.className = className;
        }
        
        @Override
        public String getDriverClassName() {
            return className;
        }
    }

    public enum MaxcomputeDriver implements Driver {
        /**
         * 默认驱动.
         */
        DEFAULT("com.aliyun.odps.jdbc.OdpsDriver");

        /**
         * Maxcompute 驱动类名.
         */
        private final String className;

        /**
         * 构造方法.
         *
         * @param className 驱动类名
         */
        MaxcomputeDriver(String className) {
            this.className = className;
        }

        @Override
        public String getDriverClassName() {
            return className;
        }
    }
    //endregion
}

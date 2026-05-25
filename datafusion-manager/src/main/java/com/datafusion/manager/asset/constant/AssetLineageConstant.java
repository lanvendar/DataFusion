package com.datafusion.manager.asset.constant;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
public class AssetLineageConstant {

    /**
     * maxcompute脚本文件结尾.
     */
    public static final String MAXCOMPUTE_SCRIPT_SUFFIX = ".osql";

    /**
     * sql脚本文件结尾.
     */
    public static final String SQL_SCRIPT_SUFFIX = ".sql";

    /**
     * 临时表后缀占位符.
     */
    public static final String TMP_TABLE_SUFFIX = "${data_distz}";

    /**
     * 临时表后缀值.
     */
    public static final String TMP_TABLE_SUFFIX_VALUE = "8l";

    /**
     * 数仓脚本git项目名.
     */
    public static final String PROJECT_NAME = "secp-dolphin-job-script";

    /**
     * Hologres call,创建表用法.
     */
    public static final String HG_CREATE_TABLE_LIKE = "HG_CREATE_TABLE_LIKE";

    /**
     * Hologres call,覆盖写入函数.
     */
    public static final String HG_INSERT_OVERWRITE_WRAPPER = "HG_INSERT_OVERWRITE_WRAPPER";

    /**
     * Hologres call,insert overwrite函数.
     */
    public static final String HG_INSERT_OVERWRITE = "HG_INSERT_OVERWRITE";

    /**
     * 边属性-键值-测点集合.
     */
    public static final String TAG_SET = "tagSet";

}

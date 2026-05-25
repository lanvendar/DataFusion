package com.datafusion.manager.development.service.sql;

import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开发侧SQL脚本拆分、清洗与数据源校验工具.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
public final class SqlScriptUtils {

    /**
     * 单行注释匹配.
     */
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile("--[^\\n]*");

    /**
     * 私有构造函数, 禁止实例化.
     */
    private SqlScriptUtils() {
    }

    /**
     * 校验数据源类型是否支持执行SQL脚本.
     *
     * @param dsEntity 数据源
     */
    public static void checkSqlExecutable(DataSourceInfoEntity dsEntity) {
        String dbType = dsEntity.getDatabaseType();
        if (!StringUtils.equalsAny(dbType,
                DatabaseTypeEnum.POSTGRES.getType(),
                DatabaseTypeEnum.HOLOGRES.getType(),
                DatabaseTypeEnum.MAXCOMPUTE.getType())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "数据源类型不支持执行SQL");
        }
    }

    /**
     * 把脚本按 ; 切分并去掉注释, 返回每段以 ; 结尾的SQL列表.
     *
     * @param script 原始SQL脚本
     * @return 拆分后的SQL列表
     */
    public static List<String> splitAndClean(String script) {
        List<String> validStatements = new ArrayList<>();
        if (StringUtils.isBlank(script)) {
            return validStatements;
        }
        String[] sqlStatements = script.split("(?<!\\\\);");
        for (String statement : sqlStatements) {
            String trimmedStatement = cleanSqlStatement(statement.trim());
            if (!trimmedStatement.isEmpty()) {
                validStatements.add(trimmedStatement + ";");
            }
        }
        return validStatements;
    }

    /**
     * 删除SQL中的单行注释.
     *
     * @param sql sql脚本
     * @return 去掉注释的SQL
     */
    private static String cleanSqlStatement(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        Matcher singleLineMatcher = SINGLE_LINE_COMMENT_PATTERN.matcher(sql);
        return singleLineMatcher.replaceAll("");
    }
}

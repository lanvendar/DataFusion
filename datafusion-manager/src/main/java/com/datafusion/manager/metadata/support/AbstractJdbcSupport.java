package com.datafusion.manager.metadata.support;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.ConnectTypeEnum;
import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.common.type.DataTypeFamily;
import com.datafusion.common.type.Type;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.dto.ColumnViewConfigDto;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareInfo;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareResultDto;
import com.datafusion.manager.metadata.enums.TableColumnCompareEnum;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.support.model.DataSourceExtendParam;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.MetaDataQuery;
import com.datafusion.manager.utils.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static com.datafusion.common.constant.SystemConstant.LINE_FEED;

/**
 * 抽线数据库服务.
 *
 * @param <T> 元数据返回结果泛型
 * @author david
 * @version 3.6.4, 2024/9/9
 * @since 3.6.4, 2024/9/9
 */
@Slf4j
public abstract class AbstractJdbcSupport<T> implements MetaDataSupport, TransformSupport {
    
    /**
     * 常用的时区.
     */
    protected static List<String> commonTimezones = Arrays.asList(
            "Asia/Shanghai", "UTC", "Asia/Tokyo", "Asia/Hong_Kong", "Asia/Singapore",
            "Asia/Seoul", "Asia/Dubai", "Europe/London", "Europe/Berlin",
            "Europe/Paris", "Europe/Madrid", "Europe/Rome", "Europe/Istanbul",
            "America/New_York", "America/Los_Angeles", "America/Chicago",
            "America/Denver", "America/Mexico_City", "America/Sao_Paulo",
            "America/Buenos_Aires", "Africa/Cairo", "Africa/Johannesburg",
            "Africa/Nairobi", "Australia/Sydney", "Australia/Melbourne",
            "Australia/Brisbane", "Etc/GMT"
    );
    
    /**
     * 常用编码格式.
     */
    protected static List<String> commonEncodings = Arrays.asList(
            "UTF-8", "UTF-16", "UTF-32", "ISO-8859-1", "GBK", "GB2312", "Big5",
            "Shift_JIS", "EUC-JP", "Windows-1252"
    );
    
    /**
     * sql 解析模板.
     */
    private final JFinalSqlBuilder builder = JFinalSqlBuilder.create().build();
    
    @Override
    public Set<String> getColumnDataTypes() {
        Type definition = TypeInfoManager.getDefinition(support());
        return definition.getFieldTypeList();
    }
    
    @Override
    public List<DataSourceExtendParam> getDefaultExtendParams() {
        //  {"characterEncoding":"UTF-8","autoReconnect":"true","useSSL":"false","serverTimezone":"Asia/Shanghai"}
        DataSourceExtendParam characterEncoding =
                DataSourceExtendParam.builder().name("编码").identifier("characterEncoding").value("UTF-8")
                        .defaultValue("UTF-8").options(commonEncodings).build();
        
        DataSourceExtendParam autoReconnect =
                DataSourceExtendParam.builder().name("自动重连").identifier("autoReconnect").value("true")
                        .defaultValue("true").options(Arrays.asList("true", "false")).build();
        
        DataSourceExtendParam useSSL =
                DataSourceExtendParam.builder().name("SSL").identifier("useSSL").value("false")
                        .defaultValue("false").options(Arrays.asList("true", "false")).build();
        
        DataSourceExtendParam serverTimezone =
                DataSourceExtendParam.builder().name("时区").identifier("serverTimezone")
                        .value("Asia/Shanghai").defaultValue("Asia/Shanghai")
                        .options(commonTimezones).build();
        
        return Arrays.asList(characterEncoding, autoReconnect, useSSL, serverTimezone);
    }
    
    /**
     * 根据数据源实体转换数据源信息.
     *
     * @param dsEntity 数据源实体信息
     * @return 数据源信息
     */
    @Override
    public DataSourceInfo transformDataSourceInfo(DataSourceInfoEntity dsEntity) {
        DataSourceInfo info = new DataSourceInfo();
        BeanUtils.copyProperties(dsEntity, info);
        //判断密码有没有修改？
        if (StrUtil.isNotEmpty(info.getPassword())) {
            if (AesUtil.isSecret(info.getPassword())) {
                info.setPassword(AesUtil.decrypt(info.getPassword()));
            }
        }
        info.setConnectType(ConnectTypeEnum.JDBC.getConnectType());
        info.setDriverClass(getDefaultDriverClass());
        info.setJdbcUrl(generateJdbcUrl(info));
        return info;
    }
    
    /**
     * 获取默认驱动类.
     *
     * @return 默认驱动类
     */
    protected abstract String getDefaultDriverClass();
    
    /**
     * 根据数据源信息生成 JdbcUrl.
     *
     * @param info 数据源信息
     * @return JdbcUrl
     */
    protected abstract String generateJdbcUrl(DataSourceInfo info);
    
    /**
     * 将 Properties 转换为 JDBC 连接参数字符串.
     * 这是一个推荐的、使用 Java 8 Stream API 的现代实现。
     *
     * @param extendParam 包含连接参数的 Properties 对象
     * @return 格式化后的连接参数字符串 (e.g., "key1=value1&key2=value2")
     */
    protected String parseExtendParam(Properties extendParam) {
        // 1. 处理 null 或空输入的情况，返回空字符串
        if (extendParam == null || extendParam.isEmpty()) {
            return "";
        }
        
        // 2. 使用 Stream API 进行转换和拼接
        return extendParam.stringPropertyNames().stream()
                .map(key -> {
                    String value = extendParam.getProperty(key);
                    // 确保 key 和 value 都不为 null，避免编码 null 值
                    if (key != null && value != null) {
                        return URLEncoder.encode(key, StandardCharsets.UTF_8) + SystemConstant.EQ //
                                + URLEncoder.encode(value, StandardCharsets.UTF_8);
                    }
                    return null; // 如果 key 或 value 为 null，则忽略此条目
                })
                .filter(java.util.Objects::nonNull) // 过滤掉被忽略的条目
                .collect(Collectors.joining(SystemConstant.AND_SERVER));
    }
    
    /**
     * 根据数据源实体信息,获取对应数据库的表字段信息.
     *
     * @param dsInfo 数据源实体
     * @return 数据库表字段信息
     */
    @Override
    public final MetaDataInfo getMetaData(DataSourceInfo dsInfo) {
        return getMetaData(dsInfo, null);
    }
    
    /**
     * 根据数据源实体及表名称信息，获取对应数据库的表字段信息.
     *
     * @param dsInfo         数据源实体
     * @param queryCondition 表名称集合
     * @return 指定数据库表字段信息
     */
    @Override
    public MetaDataInfo getMetaData(DataSourceInfo dsInfo, MetaDataQuery queryCondition) {
        T data;
        if (CollectionUtil.isEmpty(queryCondition.getTableNames())) {
            data = queryMetaData(dsInfo, null);
        } else {
            data = queryMetaData(dsInfo, queryCondition.getTableNames());
        }
        // TODO 可补充MetaDataQuery.tableNamePrefix的逻辑
        
        MetaDataInfo metadata = null;
        if (null != data) {
            metadata = transformMetaData(dsInfo, data);
        }
        return metadata;
    }
    
    /**
     * 根据数据源信息及表名称集合，获取对应数据库的表字段信息.
     *
     * @param dsInfo     数据源信息
     * @param tableNames 表名称集合
     * @return 数据库表字段信息
     */
    protected abstract T queryMetaData(DataSourceInfo dsInfo, List<String> tableNames);
    
    /**
     * 根据各数据库返回的数据库表字段信息转换元数据信息.
     *
     * @param ds          数据源信息
     * @param tableColumn 数据库表字段信息
     * @return 元数据信息
     */
    protected abstract MetaDataInfo transformMetaData(DataSourceInfo ds, T tableColumn);

    @Override
    public ColumnViewConfigDto getDataTypeViewConfig(String dataType) {
        TypeInfo typeInfo = TypeInfoManager.parse(support(), dataType);
        //   - PrecScale.NO_NO，不需要展示【字段长度】【字段精度位】【字段小数位】
        //   - PrecScale.NO_NO | PrecScale.YES_NO 且 DataTypeFamily.TEXT，只展示【字段长度】
        //   - PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES 且 DataTypeFamily.NUMERIC && DataTypeFamily.DATE 展示【字段精度位】【字段小数位】
        if (typeInfo.getDataType() != null) {
            if (typeInfo.getDataType().getSignatures() == 1) {
                return new ColumnViewConfigDto(0, 0,  0);
            } else if (typeInfo.getDataType().getSignatures() == 3
                    && typeInfo.getDataType().getDataTypeFamily().equals(DataTypeFamily.TEXT)) {
                return new ColumnViewConfigDto(1, 0,  0);
            } else if (typeInfo.getDataType().getSignatures() == 7
                    && (typeInfo.getDataType().getDataTypeFamily().equals(DataTypeFamily.NUMERIC)
                    || typeInfo.getDataType().getDataTypeFamily().equals(DataTypeFamily.DATE))) {
                return new ColumnViewConfigDto(0, 1,  1);
            } else {
                return new ColumnViewConfigDto(0, 0,  0);
            }
        } else {
            return new ColumnViewConfigDto(0, 0,  0);
        }
    }
    
    /**
     * 获取表的更新语句.
     *
     * @param info 数据源信息
     * @param compareResultDto 表字段对比信息
     * @return 修改表DDL
     */
    @Override
    public String getAlterTableSql(DataSourceInfo info, TableColumnInfoCompareResultDto compareResultDto) {
        StringBuilder sb = new StringBuilder();
        
        String  tableName = compareResultDto.getTargetColumns().get(0).getTableName();
        compareResultDto.getSourceColumns().forEach(m -> {
            m.setTableName(tableName);
        });
        sb.append("--").append(tableName).append("表结构修改").append(LINE_FEED);
        // 处理新增字段
        List<TableColumnInfoCompareInfo> newColumns = compareResultDto.getSourceColumns().stream()
                .filter(col -> col.getCompareResult() == TableColumnCompareEnum.NEW)
                .collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(newColumns)) {
            newColumns.sort(Comparator.comparing(TableColumnInfoCompareInfo::getColumnSerial));
            // Maxcompute的ADD COLUMNS可以批量添加，但JFinal Enjoy模板的迭代生成可能需要每个单独调用模板。
            // 这里为了简化，我们为每个新增列生成一个 ADD COLUMNS 语句
            // 或者可以设计一个模板批量添加，但更通用的做法是每个操作对应一个语句
            for (TableColumnInfoCompareInfo col : newColumns) {
                Map<String, Object> params = new HashMap<>();
                params.put("columnInfo", col);
                sb.append(builder.renderSql(support().getType() + ".alterTable_addColumn", params).getSql()).append(LINE_FEED);
            }
        }
        
        // 处理存在差异的字段 (修改类型或注释)
        List<TableColumnInfoCompareInfo> differentColumns = compareResultDto.getSourceColumns().stream()
                .filter(col -> col.getCompareResult() == TableColumnCompareEnum.DIFFERENT)
                .collect(Collectors.toList());
        
        if (CollectionUtil.isNotEmpty(differentColumns)) {
            for (TableColumnInfoCompareInfo col : differentColumns) {
                // 对于 DIFFERNET，我们假设列名不变，只修改类型或注释
                Map<String, Object> params = new HashMap<>();
                params.put("columnInfo", col);
                sb.append(builder.renderSql(support().getType() + ".alterTable_changeColumn", params).getSql()).append(LINE_FEED);
            }
        }
    
        // 处理存在差异的字段 (修改类型或注释)
        List<TableColumnInfoCompareInfo> deleteColumns = compareResultDto.getSourceColumns().stream()
                .filter(col -> col.getCompareResult() == TableColumnCompareEnum.DELETE)
                .collect(Collectors.toList());
    
        if (CollectionUtil.isNotEmpty(deleteColumns)) {
            for (TableColumnInfoCompareInfo col : deleteColumns) {
                // 对于 DIFFERNET，我们假设列名不变，只修改类型或注释
                Map<String, Object> params = new HashMap<>();
                params.put("columnInfo", col);
                sb.append(builder.renderSql(support().getType() + ".alterTable_deleteColumn", params).getSql()).append(LINE_FEED);
            }
        }
        
        // 处理缺失字段 (DELETE)。
        // Maxcompute 不直接支持 ALTER TABLE DROP COLUMN。
        // 这种情况下，通常需要更复杂的策略，例如：
        // 1. 如果是开发环境，可能允许手动删除或重建表。
        // 2. 如果是生产环境，可能需要数据迁移（CTAS）或保留列。
        // 鉴于此，我们在这里不生成 DROP COLUMN 的 DDL。
        // 如果你需要处理这种情况，你需要考虑更复杂的逻辑，可能涉及 CREATE TABLE AS SELECT (CTAS)
        // maxcompute 不让修改字段类型，只能修改字段注释
        return sb.toString();
    }
}

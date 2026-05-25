package com.datafusion.manager.metadata.dto;

import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 数据库表字段sheet页信息,用于描述每个设备sheet的数据信息.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/6
 * @since 3.7.2, 2024/11/6
 */
@Data
public class TableColumnExcelSheet {
    /**
     * excel sheet 索引号.
     */
    private int sheetIndex;

    /**
     * excel sheet名称.
     */
    private String sheetName;

    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;

    /**
     * 表注释.
     */
    @Schema(name = "tableDesc", description = "表注释")
    private String tableDesc;

    /**
     * 列-行表头.
     */
    private List<String> header = Lists.newArrayList();

    /**
     * 行数据.
     */
    private List<ColumnInfoEntity> rows;

    /**
     * 构造函数.
     *
     * @param index excel sheet索引号
     * @param name  excel sheet 名称
     */
    public TableColumnExcelSheet(int index, String name) {
        this.sheetIndex = index;
        this.sheetName = name;
    }
}


package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.List;


/**
 * TableColumnInfo MaxcomputeTableCreateParam元数据结构结果对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/2/10
 * @since 2023/2/10
 */
@Data
public class MaxcomputeTableCreateParam {

    /**
     *  tableInfo.
     */
    private TableInfo tableInfo;

    /**
     * columnInfos.
     */
    private List<TableColumnInfo> columnInfos;

    /**
     * partitionColumnInfos.
     */
    private List<TableColumnInfo> partitionColumnInfos;

}

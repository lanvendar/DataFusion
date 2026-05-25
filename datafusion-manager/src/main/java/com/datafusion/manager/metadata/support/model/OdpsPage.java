package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.List;

/**
 * 元数据信息.
 *
 * @param <T> 元数据对象
 * @author david
 * @version 3.6.4, 2024/8/13
 * @since 3.6.4, 2024/8/13
 */
@Data
public class OdpsPage<T> {
    
    /**
     * pageNo.
     */
    private long pageNo;
    
    /**
     * pageSize.
     */
    private long pageSize;
    
    /**
     * total.
     */
    private long total;
    
    /**
     * pages.
     */
    private long pages;
    
    /**
     * records.
     */
    private List<T> records;
    
    /**
     * OdpsPage.
     *
     * @param pageNo   pageNo
     * @param pageSize pageSize
     * @param total    total
     */
    public OdpsPage(long pageNo, long pageSize, long total) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        if (pageSize > 0) {
            this.pages = (total + pageSize - 1) / pageSize;
        } else {
            this.pages = 0;
        }
    }
    
}

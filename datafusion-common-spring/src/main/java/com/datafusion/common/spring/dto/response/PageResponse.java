package com.datafusion.common.spring.dto.response;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页请求返回数据.
 *
 * @param <T> 返回数据类型
 * @author xufeng
 * @version 1.0.0, 2025/8/27
 * @since 2025/8/27
 */
@Data
@Schema(name = "PageResponse", description = "分页请求返回数据")
@AllArgsConstructor
public class PageResponse<T> {
    
    /**
     * 排序的数据集合.
     */
    @Schema(name = "dataList", description = "排序的数据集合")
    private List<T> dataList;
    
    /**
     * 单页的数量.
     */
    @Schema(name = "size", description = "单页的数量")
    private Integer size;
    
    /**
     * 当前页数（从1开始）.
     */
    @Schema(name = "current", description = "当前页数（从1开始）")
    private Integer current;
    
    /**
     * 总数量.
     */
    @Schema(name = "total", description = "总数量")
    private Integer total;
    
    /**
     * PageResponse.
     */
    public PageResponse() {
        this.total = 0;
        dataList = Collections.emptyList();
    }
    
    /**
     * emptyPage.
     *
     * @param pageQuery pageQueryRequest
     * @param <T>       t
     * @return t
     */
    public static <T> PageResponse<T> emptyPage(PageQuery pageQuery) {
        PageResponse<T> response = new PageResponse<T>();
        response.setSize(pageQuery.getSize());
        response.setCurrent(pageQuery.getCurrent());
        response.setTotal(0);
        response.setDataList(Collections.emptyList());
        return response;
    }
}

package com.datafusion.common.spring.dto.request.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 条件筛选+字段过滤+排序的分页请求对象.
 *
 * @param <T> 查询对象
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQueryOrder<T> extends PageQuery<T> {
    
    /**
     * 构造函数.
     *
     * @param size    每页数量
     * @param current 页码数
     * @param option  页码数
     * @param keyword 显示字段: column1,column2,column3...
     * @param orderBy 排序字段
     */
    public PageQueryOrder(Integer size, Integer current, T option, String keyword, List<Order> orderBy) {
        super(size, current, option);
        this.keyword = keyword;
        this.orderBy = orderBy;
    }
    
    /**
     * 构造函数.
     *
     * @param size    每页数量
     * @param current 页码数
     * @param option  页码数
     */
    public PageQueryOrder(Integer size, Integer current, T option) {
        super(size, current, option);
    }
    
    /**
     * 普通表,排除分区表.
     */
    @Schema(name = "keyword", description = "搜索关键字")
    private String keyword;
    
    /**
     * 排序结合对象.
     */
    @Schema(name = "orderBy", description = "排序结合对象")
    private List<Order> orderBy;
}

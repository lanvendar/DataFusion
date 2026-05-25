package com.datafusion.common.spring.dto.request.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 条件筛选的分页请求对象.
 *
 * @param <T> 查询对象
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PageQuery<T> extends SimplePageQuery {
    /**
     * 构造函数.
     *
     * @param size    页大小
     * @param current 页码数
     * @param option  查询对象
     */
    public PageQuery(Integer size, Integer current, T option) {
        super(size, current);
        this.option = option;
    }

    /**
     * 构造函数.
     *
     * @param option 页码数
     */
    public PageQuery(T option) {
        this.option = option;
    }

    /**
     * 普通表,排除分区表.
     */
    @Schema(name = "option", description = "查询对象")
    private T option;

    /**
     * 计算分页时的offset.
     * @return int 返回分页时的offset
     */
    @Schema(name = "offset", description = "分页时的offset", hidden = true)
    public int getOffset() {
        return (this.getCurrent() - 1) * this.getSize();
    }
}

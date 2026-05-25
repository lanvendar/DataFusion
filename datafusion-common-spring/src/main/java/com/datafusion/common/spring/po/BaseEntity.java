package com.datafusion.common.spring.po;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 表通用实体字段.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Data
@Accessors(chain = true)
public abstract class BaseEntity implements Serializable {
    /**
     * 创建人.
     */
    @TableField("creator")
    private String creator;
    
    /**
     * 创建时间.
     */
    @TableField("create_time")
    private Date createTime;
    
    /**
     * 更新人.
     */
    @TableField("updater")
    private String updater;
    
    /**
     * 更新时间.
     */
    @TableField("update_time")
    private Date updateTime;
}

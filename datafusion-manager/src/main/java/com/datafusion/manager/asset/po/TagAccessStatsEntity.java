package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * tag_access_stats 实体类.
 *
 * @author DataFusion
 * @version 1.0.0
 */
@Data
@TableName("tag_access_stats")
public class TagAccessStatsEntity {

    /**
     * 主键ID.
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标签.
     */
    private String tag;

    /**
     * 时间维度.
     */
    private String dimension;

    /**
     * URI.
     */
    private String uri;
}

package com.datafusion.common.spring.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 基础公共类型.
 *
 * @author lanvendar
 * @version 1.0.0, 2021/2/25
 * @since 2021/2/25
 */
@Data
@Accessors(chain = true)
public abstract class BaseIdEntity extends BaseEntity {
    
    /**
     * 物理主键ID.
     */
    @TableId("id")
    @TableField("id")
    private UUID id;
}

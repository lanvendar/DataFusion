package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/14
 * @since 2025/11/14
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_lineage_interface_tmp")
public class InterfaceEntity extends BaseIdEntity {
    
    /**
     * 接口服务.
     */
    @TableField("service_name")
    private String serviceName;
    
    /**
     * 接口方法类型,GET,POST.
     */
    @TableField("method_type")
    private String methodType;
    
    /**
     * 接口url.
     */
    @TableField("interface_url")
    private String interfaceUrl;
    
    /**
     * skywalking中的服务id.
     */
    @TableField("service_id")
    private String serviceId;
    
    /**
     * skywalking中的服务的终端id.
     */
    @TableField("endpoint_id")
    private String endpointId;
    
    /**
     * skywalking中的服务的终端name.
     */
    @TableField("endpoint_name")
    private String endpointName;
    
    /**
     * 状态.
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 应用Code.
     */
    @TableField("app_code")
    private String appCode;
    
    /**
     * 菜单叶子节点code.
     */
    @TableField("menu_code")
    private String menuCode;
    
}

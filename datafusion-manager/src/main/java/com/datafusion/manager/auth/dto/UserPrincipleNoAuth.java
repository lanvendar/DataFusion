package com.datafusion.manager.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户认证信息.
 * @author xufeng
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
@Data
public class UserPrincipleNoAuth {

    /**
     * 主键（自增ID）.
     */
    private Long id;

    /**
     * 用户名.
     */
    private String username;

    /**
     * 密码（sha256+盐）加密.
     */
    @JsonIgnore
    private String pwd;

    /**
     * 密码重置时间.
     */
    private LocalDateTime pwdResetTime;

    /**
     * 密码发送方式,多个以逗号分割(sms-短信，mail-邮件).
     */
    private String pwdSendType;

    /**
     * 真实姓名.
     */
    private String name;

    /**
     * 手机号.
     */
    private String phone;

    /**
     * 邮箱地址.
     */
    private String mail;

    /**
     * 性别(0-male,1-female).
     */
    private Integer sex;

    /**
     * 是否启用.
     */
    private Boolean enable;

    /**
     * 租户id.
     */
    private Long tenantId;

    /**
     * 租户名称.
     */
    private String tenantName;

    /**
     * 移动端识别码.
     */
    private String mobileCode;

    /**
     * 切换人的id.
     */
    private Long switchOperatorId;

    /**
     * 切换人的username信息.
     */
    private String switchOperator;

    /**
     * 切换人的name信息.
     */
    private String switchOperatorName;
}

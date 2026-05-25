package com.datafusion.common.exception;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一运行异常定义类.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/18
 * @since 2021/11/25
 */
@Slf4j
@Data
public class CommonException extends RuntimeException {
    
    private static final long serialVersionUID = -5174078676744023015L;
    
    /**
     * 错误码.
     */
    protected String code;
    
    /**
     * 错误描述.
     */
    protected String description;
    
    /**
     * 错误消息对应语言键.
     */
    protected String errorKey;
    
    /**
     * 错误消息对应语言键.
     */
    protected Exception exception;
    
    /**
     * 自定义信息构造函数.
     *
     * @param description 错误描述
     */
    public CommonException(String description) {
        super(description);
        this.description = description;
    }
    
    /**
     * 自定义信息构造函数.
     *
     * @param description 错误信息
     * @param e           异常
     */
    public CommonException(String description, Exception e) {
        super(CharSequenceUtil.isNotBlank(description) ? description : (e != null ? e.getMessage() : null), e);
        this.description = CharSequenceUtil.isNotBlank(description) ? description : (e != null ? e.getMessage() : null);
        this.exception = e;
    }
    
    /**
     * 预定义错误码构造函数.
     *
     * @param errorCode 通用错误码
     */
    public CommonException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.description = errorCode.getDescription();
        this.errorKey = errorCode.getDefaultKey();
    }
    
    /**
     * 预定义错误码+自定义信息构造函数.
     *
     * @param errorCode   通用错误码
     * @param description 错误信息
     */
    public CommonException(ErrorCode errorCode, String description) {
        this.code = errorCode.getCode();
        this.description = CharSequenceUtil.isNotBlank(description) ? description : errorCode.getDescription();
        this.errorKey = errorCode.getDefaultKey();
    }
    
    /**
     * 预定义错误码+自定义信息构造函数.
     *
     * @param errorCode   通用错误枚举
     * @param description 错误信息
     * @param errorKey    错误消息对应语言键
     */
    public CommonException(ErrorCode errorCode, String description, String errorKey) {
        this.code = errorCode.getCode();
        this.description = CharSequenceUtil.isNotBlank(description) ? description : errorCode.getDescription();
        this.errorKey = CharSequenceUtil.isNotBlank(errorKey) ? errorKey : errorCode.getDefaultKey();
    }
    
    @Override
    public String getMessage() {
        return CharSequenceUtil.isNotBlank(description) ? description : (exception != null ? exception.getMessage() : super.getMessage());
    }
    
    @Override
    public String toString() {
        return "CommonException{" + "code='" + code + '\'' + ", description='" + description + '\'' + ", errorKey='"
                + errorKey + '\'' + (exception != null ? exception.getMessage() : super.getMessage()) + '}';
    }
}

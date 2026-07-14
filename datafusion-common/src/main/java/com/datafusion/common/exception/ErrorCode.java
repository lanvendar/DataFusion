package com.datafusion.common.exception;

/**
 * 错误码接口.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/27
 * @since 2025/8/27
 */
public interface ErrorCode {
    
    /**
     * 获取错误码.
     *
     * @return String
     */
    String getCode();

    /**
     * 获取错误描述.
     *
     * @return String
     */
    String getDescription();
    
    /**
     * 获取默认国际化语言键，为了向前兼容，对未国际化的错误码枚举，默认返回null.
     *
     * @return String
     */
    default String getDefaultKey() {
        return null;
    }
}

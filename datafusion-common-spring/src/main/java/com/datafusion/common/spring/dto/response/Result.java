package com.datafusion.common.spring.dto.response;

import com.datafusion.common.exception.ErrorCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 返回消息统一包装.
 *
 * @param <T> 返回数据类型
 * @author xufeng
 * @version 1.0.0, 2025/8/27
 * @since 2025/8/27
 */
@Data
public class Result<T> {

    /**
     * 状态码.
     */
    @Schema(name = "code", description = "状态码", example = "00000")
    private String code;

    /**
     * 消息.
     */
    @Schema(name = "description", description = "描述")
    private String description;

    /**
     * 错误消息.
     */
    @Schema(name = "errorMsg", description = "错误消息")
    private String errorMsg;

    /**
     * 错误消息对应语言键.
     */
    @Schema(name = "errorKey", description = "错误消息对应语言键")
    private String errorKey;

    /**
     * 返回数据.
     */
    @Schema(name = "data", description = "返回数据")
    private T data;

    /**
     * Result.
     */
    public Result() {
    }

    /**
     * Result.
     *
     * @param code        code
     * @param description description
     * @param errorMsg    errorMsg
     * @param data        data
     */
    public Result(String code, String description, String errorMsg, T data) {
        this.code = code;
        this.description = description;
        this.errorMsg = errorMsg;
        this.errorKey = null;
        this.data = data;
    }

    /**
     * Result.
     *
     * @param code        code
     * @param description description
     * @param errorMsg    errorMsg
     * @param errorKey    errorKey
     * @param data        data
     */
    public Result(String code, String description, String errorMsg, String errorKey, T data) {
        this.code = code;
        this.description = description;
        this.errorMsg = errorMsg;
        this.errorKey = errorKey;
        this.data = data;
    }

    /**
     * 通用返回类.
     *
     * @param data 成功返回数据
     * @param <T>  返回数据类型
     * @return Result&lt;T> 返回成功结构体
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getDescription(),
                null, ErrorCodeEnum.SUCCESS.getDefaultKey(), data);
    }

    /**
     * 通用返回类.
     *
     * @param errorCodeEnum 成功返回数据
     * @param <T>           返回数据类型
     * @return Result&lt;T> 返回成功结构体
     */
    public static <T> Result<T> failed(ErrorCodeEnum errorCodeEnum) {
        return new Result<>(errorCodeEnum.getCode(), errorCodeEnum.getDescription(),
                null, errorCodeEnum.getDefaultKey(), null);
    }

    /**
     * 通用返回类.
     *
     * @param errorCodeEnum 成功返回数据
     * @param <T>           返回数据类型
     * @param errorMsg      错误信息
     * @return Result&lt;T> 返回成功结构体
     */
    public static <T> Result<T> failed(ErrorCodeEnum errorCodeEnum, String errorMsg) {
        return new Result<>(errorCodeEnum.getCode(), errorCodeEnum.getDescription(),
                errorMsg, errorCodeEnum.getDefaultKey(), null);
    }

}


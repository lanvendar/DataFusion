package com.datafusion.manager.config;

import cn.hutool.core.util.StrUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.web.dto.response.Result;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

@Order(1)
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理 @RequestBody 参数校验失败.
     *
     * @param e 全局异常
     * @return 提示结果
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, CommonException.class, SQLException.class, Exception.class})
    public Result<Object> handleValidationExceptions(Exception e) {
        e.printStackTrace();
        if (e instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            BindingResult bindingResult = ex.getBindingResult();
            if (bindingResult.hasErrors()) {
                FieldError fieldError = bindingResult.getFieldError();
                if (fieldError != null) {
                    String errorMessage = fieldError.getDefaultMessage();
                    if (StrUtil.isNotBlank(errorMessage)) {
                        return Result.failed(ErrorCodeEnum.USER_INVALID_PARAM_A0154, errorMessage);
                    }
                }
            }
        } else if (e instanceof CommonException) {
            CommonException ex = (CommonException) e;
            return Result.failed(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, ex.getMessage());
        } else if (e instanceof SQLException) {
            return Result.failed(ErrorCodeEnum.SERVICE_SQL_EXCUTE_ERROR_C0313, e.getMessage());
        }
        return Result.failed(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505);
    }
}
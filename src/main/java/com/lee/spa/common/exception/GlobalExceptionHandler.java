package com.lee.spa.common.exception;

import com.lee.spa.common.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(
            BusinessException e) {

        return Result.fail(e.getMessage());
    }

    /**
     * 处理重复预约异常
     * @param e
     * @return
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(
            DuplicateKeyException e) {

        return Result.fail("该时间段刚刚已被其他用户预约，请重新选择");
    }
}

package com.lee.spa.common.exception;

import com.lee.spa.common.result.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public org.springframework.http.ResponseEntity<Result<Void>> invalidInput(Exception error) {
        return org.springframework.http.ResponseEntity.badRequest().body(new Result<>(400, "请求参数格式不正确，请核对后重试", null));
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<Result<Void>> unexpected(Exception error) {
        String reference = java.util.UUID.randomUUID().toString();
        // JDBC/HTTP exception messages may contain bind values, customer input or credentials.
        LOG.error("Request failed reference={} type={}", reference, error.getClass().getSimpleName());
        return org.springframework.http.ResponseEntity.internalServerError()
                .body(new Result<>(500, "服务暂时不可用，请稍后重试。参考编号：" + reference, null));
    }
    @ExceptionHandler(com.lee.spa.auth.AuthException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleAuthentication(com.lee.spa.auth.AuthException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatus())
                .body(new Result<>(error.getStatus(), error.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> duplicateDatabaseKey(org.springframework.dao.DuplicateKeyException error) {
        return org.springframework.http.ResponseEntity.status(409).body(new Result<>(409, "记录已存在或时段刚被占用，请刷新后重试", null));
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        return org.springframework.http.ResponseEntity.badRequest().body(new Result<>(400, "表单参数不合法：" + message, null));
    }
}

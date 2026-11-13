package com.xt.interview.config;

import jakarta.persistence.EntityExistsException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器：所有未捕获异常统一返回中文错误信息，
 * 前端只看到"数据库操作失败"这类中文提示，而不是裸的 500 错误体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：RuntimeException 直接带中文消息（如"用户名不能为空"、"账号不存在"） */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("业务异常", e);
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "服务器内部错误，请稍后重试";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, msg);
    }

    /** JPA 数据完整性冲突（重复键等） */
    @ExceptionHandler({DataIntegrityViolationException.class, EntityExistsException.class})
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(Exception e) {
        log.error("数据冲突（如主键/外键/非空约束）", e);
        return buildResponse(HttpStatus.CONFLICT, "数据保存失败，可能与已有数据冲突，请检查后重试");
    }

    /** 数据库内部错误（死锁、连接中断等） */
    @ExceptionHandler({SQLException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleSql(Exception e) {
        log.error("数据库操作失败", e);
        return buildResponse(HttpStatus.CONFLICT, "数据库操作失败，请稍后重试");
    }

    /** 请求体 JSON 解析失败（缺字段、类型不匹配） */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        log.warn("请求参数不合法: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "请求参数不合法，请检查填写内容后重试");
    }

    /** 404：接口不存在 / 静态资源缺失 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        return buildResponse(HttpStatus.NOT_FOUND, "请求的接口或资源不存在");
    }

    /** Bean Validation 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ConstraintViolationException e) {
        String first = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("参数校验失败");
        return buildResponse(HttpStatus.BAD_REQUEST, first);
    }

    /** 未识别异常兜底：返回通用中文，避免把堆栈细节泄露给前端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("系统内部错误", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("status", status.value());
        body.put("timestamp", LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return ResponseEntity.status(status).body(body);
    }
}

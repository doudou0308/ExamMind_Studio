package com.exam.config;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 职责：统一捕获 Controller 层抛出的所有异常，避免丑陋的错误堆栈直接暴露给前端。
 * 相当于整个系统的"安全网"——不管哪里出了错，最终都会落到这里，
 * 返回前端一个统一格式的 JSON 错误响应：{ "ok": false, "msg": "错误描述" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获所有未被局部处理的异常
     *
     * @param e 异常对象
     * @return 统一格式的错误信息 Map，包含 ok=false 和具体错误消息
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handle(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) 
            msg = "服务器内部错误";
            return Map.of("ok", false, "msg", msg);
    }
}

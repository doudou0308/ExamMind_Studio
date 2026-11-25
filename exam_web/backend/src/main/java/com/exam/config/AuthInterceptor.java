package com.exam.config;

import com.exam.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 认证与授权拦截器
 * <p>
 * 职责：在请求到达 Controller 之前进行身份校验和权限判断。
 * 流程分为三个阶段：
 * <ol>
 *   <li><b>身份认证</b>：从请求头/参数中提取 token，验证用户是否已登录</li>
 *   <li><b>权限授权</b>：对敏感操作（增删改查用户/题目/考试）检查是否为 admin 角色</li>
 *   <li><b>清理收尾</b>：请求结束后清理 ThreadLocal 中的用户信息</li>
 * </ol>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 请求头中携带 token 的字段名 */
    private static final String TOKEN_HEADER = "Authorization";

    /**
     * 请求前置处理——"进门检查"
     * <p>
     * 在 Controller 方法执行前被调用，返回 true 放行、false 拦截。
     * 处理顺序：预检请求放行 → 白名单放行 → Token 提取 → Token 校验 → 权限检查
     *
     * @return true=放行, false=拦截并返回错误 JSON
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,Object handler) throws Exception {
    
        // 浏览器跨域预检请求（CORS preflight）直接放行，不校验 token
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        // 获取请求路径，用于判断是否为登录接口或白名单接口
        String path = request.getRequestURI();

        // 白名单：登录接口和获取学生名单接口无需 token
        if ("/api/user/login".equals(path) || "/api/user/student-names".equals(path)) return true;

        // Step 1：从请求头中获取 token
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            // 请求头没有就尝试从 URL 参数中获取（兼容某些前端传参方式）
            token = request.getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            sendError(response, 401, "请先登录");
            return false;
        }

        // Step 2：验证 token 有效性
        User user = AuthContext.getUserByToken(token);
        if (user == null) {
            sendError(response, 401, "token 无效或已过期");
            return false;
        }

        // 验证通过，将用户信息绑定到当前线程
        AuthContext.setCurrentUser(user);

        // Step 3：权限检查——以下接口仅允许 admin 角色访问
        if (path.startsWith("/api/user/add") 
                || path.startsWith("/api/user/delete")
                || path.startsWith("/api/user/reset-password")
                || path.startsWith("/api/question/add") 
                || path.startsWith("/api/question/update")
                || path.startsWith("/api/question/delete")
                || path.startsWith("/api/admin/exam/create") 
                || path.startsWith("/api/admin/exam/delete")
                || path.startsWith("/api/admin/exam/export")
                || path.startsWith("/api/admin/deepseek")
                || path.startsWith("/api/admin/wiki")) {
            if (!"admin".equals(user.getRole())) {
                sendError(response, 403, "权限不足");
                return false;
            }
        }

        return true;
    }

    /**
     * 请求完成后置处理——"出门清理"
     * <p>
     * 无论请求成功还是失败，都会执行此方法，用于释放 ThreadLocal 资源，
     * 防止线程池复用时的内存泄漏和数据串扰。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    /**
     * 向客户端返回 JSON 格式的错误响应
     *
     * @param status HTTP 状态码（如 401 未登录、403 无权限）
     * @param msg    错误提示消息
     */
    private void sendError(HttpServletResponse response, int status, String msg) throws Exception {
        // 设置响应状态码
        response.setStatus(status);
        // 设置响应内容类型为 JSON
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        // 构建错误响应体
        Map<String, Object> body = Map.of("ok", false, "msg", msg);
        // 将错误信息写入响应体
        response.getWriter().write(new ObjectMapper().writeValueAsString(body));

    }
}

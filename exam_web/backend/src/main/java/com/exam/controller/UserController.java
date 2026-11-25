package com.exam.controller;

import com.exam.config.AuthContext;
import com.exam.dao.UserDao;
import com.exam.model.User;

import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * 负责：管理员/学生登录认证、学生账号增删查、密码修改与重置
 */
@CrossOrigin(origins = "*")
@RestController// 标识为RESTful风格的控制器
@RequestMapping("/api/user")
public class UserController {
    private final UserDao dao = new UserDao();
    /**
     * 用户登录接口
     * 请求体：{username, password}
     * 返回：ok + user对象（含token、角色、是否首次登录），或错误信息
     * 密码使用MD5加密后与数据库比对
     */

    
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {


        // 从请求体中提取用户名和密码
        String username = body.get("username");
        String password = body.get("password");


        // 校验用户名和密码是否为空
        if (username == null || password == null) {
            return Map.of("ok", false, "msg", "请输入用户名和密码");
        }


        // 校验用户名是否存在
        User user = dao.findByUsername(username);
        String md5 = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));


        // 校验密码是否匹配
               if (user == null || !md5.equals(user.getPassword())) {
            return Map.of("ok", false, "msg", "用户名或密码错误");
        }


        // 登录成功：生成token令牌，组装用户信息返回
        String token = AuthContext.createToken(user);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("role", user.getRole());
        info.put("isFirstLogin", user.isFirstLogin());
        info.put("token", token);
        return Map.of("ok", true, "user", info);
    }



    /**
     * 管理员添加学生账号
     * 请求体：{username, password}
     * 新学生默认角色为"student"，首次登录标记为true
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, String> body) {

        // 从请求体中提取用户名和密码
        String username = body.get("username");
        String password = body.get("password");

        // 校验用户名和密码是否为空
        if (username == null || username.isBlank()) {
            return Map.of("ok", false, "msg", "用户名不能为空");
        }

        // 校验密码是否为空
        if (password == null || password.isBlank()) {
            return Map.of("ok", false, "msg", "密码不能为空");
        }

        // 校验用户名是否存在
        User exist = dao.findByUsername(username);
        if (exist != null) {
            return Map.of("ok", false, "msg", "账号已存在: " + username);
        }

        // 创建新用户
        // 密码使用MD5加密存储
        User user = new User();
        user.setUsername(username);
        user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8)));
        user.setRole("student");
        user.setFirstLogin(true);
        dao.create(user);
        return Map.of("ok", true, "msg", "学生添加成功");
    }

    /**
     * 分页查询学生列表（管理员端）
     * 参数：page(页码，默认1), pageSize(每页条数，默认10)
     * 返回：分页后的学生数据 + total总数
     */
    @GetMapping("/students")
    public Map<String, Object> students(
            // 分页参数
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
                // 校验页码和每页条数是否有效
        if (page < 1) page = 1;
        int offset = (page - 1) * pageSize;
        List<User> users = dao.findAllStudentsPaged(offset, pageSize);
        int total = dao.countStudents();
        List<Map<String, Object>> list = new ArrayList<>();
        // 转换为Map格式，包含ID、用户名、角色
        for (User u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("role", u.getRole());
            list.add(item);
        }
        return Map.of("ok", true, "data", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 删除学生账号
     * 路径参数：id（学生ID）
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable int id) {
        int rows = dao.deleteById(id);
        if (rows > 0) {
            return Map.of("ok", true, "msg", "学生已删除");
        }
        return Map.of("ok", false, "msg", "学生不存在");
    }

    /**
     * 管理员重置学生密码
     * 路径参数：id（学生ID）
     * 密码重置为默认值123456，学生下次登录需修改密码
     */
    @PutMapping("/reset-password/{id}")
    public Map<String, Object> resetPassword(@PathVariable int id) {
        dao.resetPassword(id);
        return Map.of("ok", true, "msg", "密码已重置为 123456，学生下次登录需修改密码");
    }

    /**
     * 当前登录用户修改密码
     * 请求体：{oldPassword, newPassword}
     * 校验：原密码正确、新密码长度≥6、新旧密码不能相同
     */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body) {

        // 从请求体中提取原密码和新密码
        User currentUser = AuthContext.getCurrentUser();
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        // 校验原密码是否为空
        if (oldPassword == null || oldPassword.isBlank()) {
            return Map.of("ok", false, "msg", "请输入原密码");
        }
        // 校验新密码是否为空
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            return Map.of("ok", false, "msg", "新密码长度不能少于6位");
        }
        String oldMd5 = DigestUtils.md5DigestAsHex(oldPassword.getBytes(StandardCharsets.UTF_8));

        // 校验原密码是否正确
        if (!oldMd5.equals(currentUser.getPassword())) {
            return Map.of("ok", false, "msg", "原密码错误");
        }

        // 校验新密码是否与原密码相同
        if (newPassword.equals(oldPassword)) {
            return Map.of("ok", false, "msg", "新密码不能与原密码相同");
        }
        // 校验新密码是否与原密码相同
        if (newPassword.equals(oldPassword)) {
            return Map.of("ok", false, "msg", "新密码不能与原密码相同");
        }
        // 校验新密码是否与原密码相同
        String newMd5 = DigestUtils.md5DigestAsHex(newPassword.getBytes(StandardCharsets.UTF_8));
        if (newMd5.equals(oldMd5)) {
            return Map.of("ok", false, "msg", "新密码不能与原密码相同");
        }


        // 更新数据库中的密码
        dao.updatePassword(currentUser.getId(), newMd5);
        currentUser.setPassword(newMd5);
        currentUser.setFirstLogin(false);
        
        return Map.of("ok", true, "msg", "密码修改成功，请重新登录");
    }




    /**
     * 获取所有学生ID和姓名（下拉选择等场景使用）
     * 返回：学生列表（仅含id和username）
     */
    @GetMapping("/student-names")
    public Map<String, Object> studentNames() {

        // 从数据库中查询所有学生ID和姓名
        List<User> students = dao.findAllStudentNames();
        List<Map<String, Object>> list = new ArrayList<>();

        // 转换为Map格式，包含ID、用户名
        for (User s : students) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("username", s.getUsername());
            list.add(item);
        }
        
        return Map.of("ok", true, "data", list);
    }
}

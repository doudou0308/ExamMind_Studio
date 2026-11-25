package com.exam.dao;

import com.exam.model.User;
import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据访问层 —— 负责 user 表的全部数据库操作
 * 包括：登录验证、创建用户、删除学生、密码管理、学生列表查询等
 */
public class UserDao {

    /**
     * 根据用户名查询用户（用于登录验证）
     * @param username 登录用户名
     * @return 匹配的 User 对象，不存在时返回 null
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM user WHERE username=?";
        Connection conn = null; 
        PreparedStatement ps = null; 
        ResultSet rs = null;
        try {
            // 连接数据库
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setString(1, username);
            rs = ps.executeQuery();
            // 校验用户是否存在且角色为学生
            if (rs.next()) return rowToUser(rs);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return null;
    }

    /**
     * 根据用户 ID 查询用户
     * @param id 用户主键
     * @return 匹配的 User 对象，不存在时返回 null
     */
    public User findById(int id) {
        String sql = "SELECT * FROM user WHERE id=?";
        // 校验用户是否存在且角色为学生
        Connection conn = null; 
        PreparedStatement ps = null; ResultSet rs = null;
        try {
            // 连接数据库
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setInt(1, id);
            rs = ps.executeQuery();
            //executeQuery返回ResultSet（查到的表格数据）
            if (rs.next()) 
                return rowToUser(rs);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return null;
    }

    /**
     * 创建新用户（管理员导入学生时调用）
     * @param user 包含用户名、密码（MD5）、角色、是否首次登录等信息的 User 对象
     * @return 受影响行数（1=成功）
     */
    public int create(User user) {
        // 校验用户是否存在且角色为学生
        String sql = "INSERT INTO user (username, password, role, is_first_login) VALUES (?, ?, ?, ?)";
        // 设置参数
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());
            ps.setBoolean(4, user.isFirstLogin());
            // 执行插入操作
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 删除学生（仅允许删除 role='student' 的用户，防止误删管理员）
     * @param id 要删除的学生 ID
     * @return 受影响行数（0=未找到或不是学生）
     */
    public int deleteById(int id) {
        String sql = "DELETE FROM user WHERE id=? AND role='student'";
        // 校验用户是否存在且角色为学生
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            // 连接数据库
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 重置学生密码为默认值 123456（MD5 加密），并标记为首次登录
     * 仅限 role='student' 的用户
     * @param id 学生 ID
     */
    public void resetPassword(int id) {
        // 校验用户是否存在且角色为学生
        String sql = "UPDATE user SET password=MD5('123456'), is_first_login=1 WHERE id=? AND role='student'";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            // 连接数据库
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 学生自主修改密码，修改后清除首次登录标记
     * @param id       学生 ID
     * @param password 新密码（已 MD5 加密的字符串）
     */
    public void updatePassword(int id, String password) {
        String sql = "UPDATE user SET password=?, is_first_login=0 WHERE id=?";
        Connection conn = null; PreparedStatement ps = null;
        // 设置参数
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, password);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 查询所有学生的 ID 和姓名（用于下拉选择等轻量场景）
     * @return 仅包含 id 和 username 字段的学生列表
     */
    public List<User> findAllStudentNames() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username FROM user WHERE role='student' ORDER BY username";
        Connection conn = null; 
        PreparedStatement ps = null; 
        ResultSet rs = null;
        try {
            // 连接数据库
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            // 处理结果集
            // 遍历结果集，将每个学生添加到列表中
            // 每个学生包含 id 和 username 字段
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                list.add(u);
            }
            // 关闭资源
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 分页查询学生列表（用于学生管理页面的分页展示）
     * @param offset 偏移量（从第几条开始）
     * @param limit  每页条数
     * @return 当前页的学生列表
     */
    public List<User> findAllStudentsPaged(int offset, int limit) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, role, is_first_login FROM user WHERE role='student' ORDER BY id LIMIT ? OFFSET ?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            // 连接数据库
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            rs = ps.executeQuery();
            // 处理结果集
            // 遍历结果集，将每个学生添加到列表中
            // 每个学生包含 id、 username、 role、 is_first_login 字段
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                u.setFirstLogin(rs.getBoolean("is_first_login"));
                list.add(u);
            }
            // 关闭资源
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 统计学生总数（用于分页计算总页数）
     * @return 学生总人数
     */
    public int countStudents() {
        String sql = "SELECT COUNT(*) FROM user WHERE role='student'";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            // 处理结果集
            // 如果结果集有数据，返回第一个字段的值（即学生总数）
            // 否则返回 0
            // 关闭资源
            if (rs.next()) 
                return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return 0;
    }

    /**
     * 将数据库的一行记录转换成 User 对象（私有工具方法）
     */
    private User rowToUser(ResultSet rs) throws SQLException {
        User u = new User();
        // 从结果集中中提取字段值
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        // 处理 is_first_login 字段，避免空指针异常
        try { u.setFirstLogin(rs.getBoolean("is_first_login")); } catch (SQLException ignored) {}
        return u;
    }
}

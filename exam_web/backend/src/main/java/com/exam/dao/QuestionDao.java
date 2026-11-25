package com.exam.dao;

import com.exam.model.Question;
import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目数据访问层 —— 负责 question 表的所有数据库操作（数据层主逻辑）
 * 包括：增删改查、分页查询、按分类/题型筛选、批量获取题目等
 *
 * 该 DAO 是遗传算法组卷的数据来源，geneticAlgorithm.evolve() 通过此 DAO 获取题库
 */
public class QuestionDao {

    /**
     * 新增一道题目到题库
     * @param q Question 对象，type 为空时默认为 "choice"
     */
    public void add(Question q) {
        String sql = "INSERT INTO question (title,option_a,option_b,option_c,option_d,answer,category,type,difficulty) VALUES (?,?,?,?,?,?,?,?,?)";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, q.getTitle());
            ps.setString(2, q.getOptionA());
            ps.setString(3, q.getOptionB());
            ps.setString(4, q.getOptionC());
            ps.setString(5, q.getOptionD());
            ps.setString(6, q.getAnswer());
            ps.setString(7, q.getCategory());
            ps.setString(8, q.getType() != null ? q.getType() : "choice");
            ps.setInt(9, q.getDifficulty());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 更新一道题目（所有字段全覆盖更新）
     * @param q 包含新数据的 Question 对象
     */
    public void update(Question q) {
        String sql = "UPDATE question SET title=?,option_a=?,option_b=?,option_c=?,option_d=?,answer=?,category=?,type=?,difficulty=? WHERE id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, q.getTitle());
            ps.setString(2, q.getOptionA());
            ps.setString(3, q.getOptionB());
            ps.setString(4, q.getOptionC());
            ps.setString(5, q.getOptionD());
            ps.setString(6, q.getAnswer());
            ps.setString(7, q.getCategory());
            ps.setString(8, q.getType() != null ? q.getType() : "choice");
            ps.setInt(9, q.getDifficulty());
            ps.setInt(10, q.getId());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 删除一道题目
     * @param id 题目 ID
     */
    public void delete(int id) {
        String sql = "DELETE FROM question WHERE id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 查询题库中的所有题目
     * @return 全部题目列表
     */
    public List<Question> listAll() {
        List<Question> list = new ArrayList<>();
        String sql = "SELECT * FROM question ORDER BY id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rowToQuestion(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 分页查询题目列表
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页的题目列表
     */
    public List<Question> listPaged(int offset, int limit) {
        List<Question> list = new ArrayList<>();
        String sql = "SELECT * FROM question ORDER BY id LIMIT ? OFFSET ?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rowToQuestion(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 按题型筛选题目（如 "choice" 选择题 / "judge" 判断题）
     * @param type 题目类型
     * @return 匹配的题目列表
     */
    public List<Question> listByType(String type) {
        List<Question> list = new ArrayList<>();
        String sql = "SELECT * FROM question WHERE type=? ORDER BY id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, type);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rowToQuestion(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 统计题库中题目的总数
     * @return 题目总数
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM question";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return 0;
    }

    /**
     * 根据 ID 查询单道题目
     * @param id 题目 ID
     * @return 匹配的 Question 对象，不存在时返回 null
     */
    public Question getById(int id) {
        String sql = "SELECT * FROM question WHERE id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            rs = ps.executeQuery();
            if (rs.next()) return rowToQuestion(rs);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return null;
    }

    /**
     * 批量查询题目（根据 ID 列表）
     * 这是遗传算法组卷后的核心查询：算法输出题目 ID 列表后，通过此方法获取完整题目信息
     * 动态构建 IN (...) 子句的占位符
     *
     * @param ids 题目 ID 列表
     * @return 题目列表
     */
    public List<Question> getByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Question> list = new ArrayList<>();
        // 动态生成 "?,?,?" 占位符
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) placeholders.append(i == 0 ? "?" : ",?");
        String sql = "SELECT * FROM question WHERE id IN (" + placeholders + ") ORDER BY id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            rs = ps.executeQuery();
            while (rs.next()) list.add(rowToQuestion(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 获取题库中所有不重复的分类名称，按字典升序排列
     * 用于前端练习模式的下拉选择
     * @return 分类名称列表
     */
    public List<String> getAllCategories() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM question WHERE category IS NOT NULL AND category != '' ORDER BY category ASC";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("category"));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 将数据库一行记录转换成 Question 对象（私有工具方法）
     * type 和 difficulty 字段兼容旧数据（可能不存在），缺失时使用默认值
     */
    private Question rowToQuestion(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setId(rs.getInt("id"));
        q.setTitle(rs.getString("title"));
        q.setOptionA(rs.getString("option_a"));
        q.setOptionB(rs.getString("option_b"));
        q.setOptionC(rs.getString("option_c"));
        q.setOptionD(rs.getString("option_d"));
        q.setAnswer(rs.getString("answer"));
        q.setCategory(rs.getString("category"));
        try { q.setType(rs.getString("type")); } catch (SQLException e) { q.setType("choice"); }
        try { q.setDifficulty(rs.getInt("difficulty")); } catch (SQLException e) { q.setDifficulty(3); }
        return q;
    }
}

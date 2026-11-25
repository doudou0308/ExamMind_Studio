package com.exam.dao;

import com.exam.model.Exam;
import com.exam.util.DBUtil;

import java.sql.*;
import java.util.*;

/**
 * 考试数据访问层 —— 负责 exam 表和 exam_question 关联表的所有数据库操作
 * 包括：创建考试、管理考试题目关联、查询考试列表、删除考试等
 *
 * 核心涉及两张表：
 *   - exam：考试基本信息（名称、管理员、时长）
 *   - exam_question：考试-题目关联表（考试包含哪些题、排序、分值、是否已删除）
 */
public class ExamDao {

    /**
     * 创建新考试（仅插入考试基本信息，不包含题目关联）
     * @param exam 包含考试名称、管理员 ID、考试时长的 Exam 对象
     * @return 新插入的考试 ID
     */
    public long create(Exam exam) {
        String sql = "INSERT INTO exam (exam_name, admin_id, duration, difficulty) VALUES (?, ?, ?, ?)";
        Connection conn = null; 
        PreparedStatement ps = null; 
        ResultSet rs = null;
        try {
            // 连接数据库
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            // 设置参数
            ps.setString(1, exam.getExamName());
            ps.setLong(2, exam.getAdminId());
            ps.setInt(3, exam.getDuration());
            ps.setDouble(4, exam.getDifficulty());
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            // 处理结果集
            // 如果结果集有数据，返回第一个字段的值（即新插入的考试 ID）
            // 否则返回 0
            // 关闭资源
            if (rs.next()) 
                return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return 0;
    }

    /**
     * 批量插入考试题目关联（创建考试后，将组卷结果写入 exam_question 表）
     * 使用 JDBC 批量执行（addBatch/executeBatch），提高大批量插入性能
     *
     * @param examId       考试 ID
     * @param questionIds  题目 ID 列表（按排序顺序）
     * @param adminId      管理员 ID（用于记录操作者）
     */
    public void insertExamQuestions(long examId, List<Integer> questionIds, long adminId) {
        String sql = "INSERT INTO exam_question (exam_id, question_id, sort_order, score, admin_id) "
                   + "VALUES (?, ?, ?, 1, ?)";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            for (int i = 0; i < questionIds.size(); i++) {
                ps.setLong(1, examId);
                ps.setInt(2, questionIds.get(i));
                ps.setInt(3, i);              // sort_order = 题目在试卷中的顺序（从0开始）
                ps.setLong(4, adminId);
                ps.addBatch();                // 加入批处理队列
            }
            ps.executeBatch();                 // 一次性提交所有插入
        } catch (SQLException e) {
            // 数据库中 exam_question 有唯一约束（exam_id + question_id），重复插入时友好提示
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new RuntimeException("考试已包含重复题目，请检查题目列表");
            }
            throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }



    
    /**
     * 查询某场考试包含的所有题目 ID（仅未删除的，按 sort_order 排序）
     * @param examId 考试 ID
     * @return 题目 ID 列表（保持试卷中的原始顺序）
     */
    public List<Integer> listQuestionIds(long examId) {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT question_id FROM exam_question WHERE exam_id=? AND is_deleted=0 ORDER BY sort_order";
        Connection conn = null; 
        PreparedStatement ps = null; 
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setLong(1, examId);
            rs = ps.executeQuery();
            // 处理结果集
            // 遍历结果集，将每个记录的 question_id 字段值添加到列表中
            while (rs.next()) list.add(rs.getInt("question_id"));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 统计考试包含的题目数量
     * @param examId 考试 ID
     * @return 有效题目的数量
     */
    public int countQuestions(long examId) {
        String sql = "SELECT COUNT(*) FROM exam_question WHERE exam_id=? AND is_deleted=0";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            // 设置参数
            ps.setLong(1, examId);
            // 执行查询
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
     * 查询考试中每道题的分值映射（题目 ID → 分数）
     * 使用 LinkedHashMap 保证与试卷题目顺序一致
     * @param examId 考试 ID
     * @return 有序的 题目ID→分值 映射表
     */
    public Map<Integer, Integer> listQuestionScores(long examId) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT question_id, score FROM exam_question WHERE exam_id=? AND is_deleted=0 ORDER BY sort_order";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, examId);
            rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getInt("question_id"), rs.getInt("score"));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return map;
    }

    /**
     * 软删除某场考试的所有题目关联（将 is_deleted 标记为 1，而非物理删除）
     * 这样可以保留历史数据，避免影响已有考试记录
     * @param examId 考试 ID
     */
    public void deleteExamQuestions(long examId) {
        String sql = "UPDATE exam_question SET is_deleted=1 WHERE exam_id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, examId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 查询所有考试列表（按创建时间倒序，最近创建的在前）
     * @return 全部考试列表
     */
    public List<Exam> listAll() {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT id, exam_name, create_time, admin_id, duration, difficulty, status FROM exam ORDER BY id DESC";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rowToExam(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 查询所有正在进行的考试列表（按创建时间倒序，最近创建的在前）
     * @return 进行中的考试列表
     */
    public List<Exam> listOngoing() {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT id, exam_name, create_time, admin_id, duration, difficulty, status FROM exam WHERE status='ongoing' ORDER BY id DESC";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            // 处理结果集
            // 如果结果集有记录，将每条记录转换为 Exam 对象并添加到列表
            while (rs.next()) list.add(rowToExam(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 分页查询考试列表
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页的考试列表
     */




    /**
     * 分页查询考试列表（按创建时间倒序，最近创建的在前）
     * @param offset 偏移量（从 0 开始）
     * @param limit  每页条数
     * @return 当前页的考试列表
     */
    public List<Exam> listPaged(int offset, int limit) {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT id, exam_name, create_time, admin_id, duration, difficulty, status FROM exam ORDER BY id DESC LIMIT ? OFFSET ?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            rs = ps.executeQuery();
            // 处理结果集
            // 如果结果集有记录，将每条记录转换为 Exam 对象并添加到列表
            while (rs.next()) list.add(rowToExam(rs));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 统计考试总数
     * @return 考试总数量
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM exam";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            // 处理结果集
            // 如果结果集有记录，返回记录数
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
     * 根据 ID 查询单个考试
     * @param id 考试 ID
     * @return 匹配的 Exam 对象，不存在时返回 null
     */
    public Exam getById(long id) {
        String sql = "SELECT id, exam_name, create_time, admin_id, duration, difficulty, status FROM exam WHERE id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            // 处理结果集
            // 如果结果集有记录，返回转换后的 Exam 对象
            if (rs.next()) return rowToExam(rs);
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return null;
    }

    /**
     * 物理删除考试（仅删除 exam 表记录，关联的 exam_question 不级联删除）
     * @param id 考试 ID
     */
    public void delete(long id) {
        String sql = "DELETE FROM exam WHERE id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }




    /**
     * 将数据库一行记录转换成 Exam 对象（私有工具方法）
     * @param rs 数据库结果集
     * @return 转换后的 Exam 对象
     * @throws SQLException 数据库操作异常
     */
    private Exam rowToExam(ResultSet rs) throws SQLException {
        Exam e = new Exam();

        e.setId(rs.getLong("id"));
        e.setExamName(rs.getString("exam_name"));
        e.setCreateTime(rs.getString("create_time"));
        e.setAdminId(rs.getLong("admin_id"));
        // 处理可能为空的字段
        try { e.setDuration(rs.getInt("duration")); } 
        catch (SQLException ignored) {}
        try { e.setDifficulty(rs.getDouble("difficulty")); }
         catch (SQLException ignored) {}
        try { e.setStatus(rs.getString("status")); } 
        catch (SQLException ignored) {}
        return e;
    }



    /**
     * 结束考试（更新考试状态为 finished）
     * @param id 考试 ID
     */
    public void finish(long id) {
        String sql = "UPDATE exam SET status='finished' WHERE id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }
}

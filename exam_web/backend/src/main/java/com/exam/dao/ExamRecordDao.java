package com.exam.dao;

import com.exam.model.ExamRecord;
import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 考试记录数据访问层 —— 负责 exam_record 表的所有数据库操作
 * 包括：提交考试答案、查询成绩列表、判断是否已交卷、删除记录等
 *
 * 核心业务场景：
 *   1. 学生提交试卷 → add() 写入一条考试记录
 *   2. 管理员查看某场考试的所有成绩 → listByExamId() / listByExamIdPaged()
 *   3. 学生查看自己的历史成绩 → listByStudentId()
 *   4. 判断学生是否已参加某场考试 → hasSubmitted()
 */
public class ExamRecordDao {
    /**
     * 新增一条考试记录（学生提交试卷时调用）
     * @param r ExamRecord 对象，包含考试 ID、学生 ID、得分、答题详情
     * @return 新插入记录的自增 ID
     */
    public long add(ExamRecord r) {
        String sql = "INSERT INTO exam_record (exam_id, student_id, score, answer_detail) VALUES (?,?,?,?)";
        Connection conn = null; 
        PreparedStatement ps = null; 
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, r.getExamId());
            ps.setLong(2, r.getStudentId());
            ps.setInt(3, r.getScore());
            ps.setString(4, r.getAnswerDetail());
            ps.executeUpdate();
            // 处理结果集
            // 如果结果集有数据，返回第一个字段的值（即新插入的记录 ID）
            // 否则返回 0
            // 关闭资源
            rs = ps.getGeneratedKeys();
            if (rs.next()) 
                return rs.getLong(1);//JDBC 老古董设计，列号从 1 起
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return 0;
    }

    /**
     * 查询所有考试记录
     */
    public List<ExamRecord> listAll() {
        List<ExamRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM exam_record ORDER BY id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                ExamRecord r = new ExamRecord();
                // 从结果集获取字段值
                // 注意：JDBC 老古董设计，列号从 1 起，而不是 0 起
                r.setId(rs.getLong("id"));
                r.setExamId(rs.getLong("exam_id"));
                r.setStudentId(rs.getLong("student_id"));
                r.setScore(rs.getInt("score"));
                r.setAnswerDetail(rs.getString("answer_detail"));
                r.setSubmitTime(rs.getString("submit_time"));
                list.add(r);
            }
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 查询某场考试的全部考试记录（按 ID 升序）
     * @param examId 考试 ID
     * @return 该考试的所有成绩记录列表
     */
    public List<ExamRecord> listByExamId(long examId) {
        List<ExamRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM exam_record WHERE exam_id=? ORDER BY id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, examId);
            rs = ps.executeQuery();
            while (rs.next()) {
                ExamRecord r = new ExamRecord();
                r.setId(rs.getLong("id"));
                r.setExamId(rs.getLong("exam_id"));
                r.setStudentId(rs.getLong("student_id"));
                r.setScore(rs.getInt("score"));
                r.setAnswerDetail(rs.getString("answer_detail"));
                r.setSubmitTime(rs.getString("submit_time"));
                list.add(r);
            }
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 根据 ID 查询单条考试记录
     * @param id 记录 ID
     * @return 匹配的 ExamRecord，不存在时返回 null
     */
    public ExamRecord getById(long id) {
        String sql = "SELECT * FROM exam_record WHERE id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                ExamRecord r = new ExamRecord();
                r.setId(rs.getLong("id"));
                r.setExamId(rs.getLong("exam_id"));
                r.setStudentId(rs.getLong("student_id"));
                r.setScore(rs.getInt("score"));
                r.setAnswerDetail(rs.getString("answer_detail"));
                r.setSubmitTime(rs.getString("submit_time"));
                return r;
            }
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return null;
    }

    /**
     * 联表查询 —— 查询某场考试的所有记录，同时带上学生姓名
     * 通过 LEFT JOIN user 表获取 student_name，用于成绩页面展示
     * @param examId 考试 ID
     * @return 含学生姓名的考试记录列表
     */
    public List<ExamRecord> listByExamIdWithStudentName(long examId) {
        List<ExamRecord> list = new ArrayList<>();
        String sql = "SELECT r.*, u.username AS student_name FROM exam_record r " +
                     "LEFT JOIN user u ON r.student_id = u.id WHERE r.exam_id=? ORDER BY r.id";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, examId);
            rs = ps.executeQuery();
            while (rs.next()) {
                ExamRecord r = new ExamRecord();
                r.setId(rs.getLong("id"));
                r.setExamId(rs.getLong("exam_id"));
                r.setStudentId(rs.getLong("student_id"));
                r.setScore(rs.getInt("score"));
                r.setAnswerDetail(rs.getString("answer_detail"));
                r.setSubmitTime(rs.getString("submit_time"));
                r.setStudentName(rs.getString("student_name"));  // 来自 user.username
                list.add(r);
            }
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 删除某场考试的所有考试记录（删除考试时调用）
     * @param examId 考试 ID
     */
    public void deleteByExamId(long examId) {
        String sql = "DELETE FROM exam_record WHERE exam_id=?";
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
     * 联表查询 —— 查询某个学生的所有考试记录，同时带上考试名称
     * 通过 LEFT JOIN exam 表获取 exam_name，用于学生端"我的成绩"页面
     * @param studentId 学生 ID
     * @return 含考试名称的成绩记录列表（按 ID 倒序，最新的在前）
     */
    public List<ExamRecord> listByStudentId(long studentId) {
        List<ExamRecord> list = new ArrayList<>();
        String sql = "SELECT r.*, e.exam_name AS exam_name FROM exam_record r " +
                     "LEFT JOIN exam e ON r.exam_id = e.id WHERE r.student_id=? ORDER BY r.id DESC";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            rs = ps.executeQuery();
            while (rs.next()) {
                ExamRecord r = new ExamRecord();
                r.setId(rs.getLong("id"));
                r.setExamId(rs.getLong("exam_id"));
                r.setStudentId(rs.getLong("student_id"));
                r.setScore(rs.getInt("score"));
                r.setAnswerDetail(rs.getString("answer_detail"));
                r.setSubmitTime(rs.getString("submit_time"));
                r.setStudentName(rs.getString("exam_name"));  // 实际存的是考试名称，复用了 studentName 字段
                list.add(r);
            }
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }

    /**
     * 判断某个学生是否已经提交了某场考试
     * 用于防止重复交卷：已交卷的学生再次进入考试页面会被拦截
     * @param examId    考试 ID
     * @param studentId 学生 ID
     * @return true=已交卷，false=未交卷
     */
    public boolean hasSubmitted(long examId, long studentId) {
        String sql = "SELECT COUNT(*) FROM exam_record WHERE exam_id=? AND student_id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return false;
    }
}

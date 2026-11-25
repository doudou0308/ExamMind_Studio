package com.exam.dao;

import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;



/**
 * 已掌握问题数据访问层
 * 提供已掌握问题相关的数据库操作
 */
public class MasteredQuestionDao {
    /**
     * 标记已掌握问题
     * @param studentId 学生 ID
     * @param questionId 问题 ID
     */ 
    public void mark(long studentId, long questionId) {
        String sql = "INSERT IGNORE INTO mastered_question (student_id, question_id) VALUES (?,?)";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            ps.setLong(2, questionId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 取消标记已掌握问题
     * @param studentId 学生 ID
     * @param questionId 问题 ID
     */ 
    public void unmark(long studentId, long questionId) {
        String sql = "DELETE FROM mastered_question WHERE student_id=? AND question_id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            ps.setLong(2, questionId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 列出已掌握问题
     * @param studentId 学生 ID
     * @return 已掌握问题 ID 列表
     */
    public List<Long> listMastered(long studentId) {
        List<Long> list = new ArrayList<>();
        String sql = "SELECT question_id FROM mastered_question WHERE student_id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getLong("question_id"));
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
        return list;
    }
}

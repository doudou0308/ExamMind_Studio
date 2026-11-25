package com.exam.dao;

import com.exam.util.DBUtil;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class WrongQuestionAnalysisDao {



    /**
     * 根据问题 ID和学生 ID获取错误问题分析
     * @param questionId 问题 ID
     * @param studentId 学生 ID
     * @return 错误问题分析
     */
    public Map<String, Object> getByQuestionAndStudent(long questionId, long studentId) {
        String sql = "SELECT * FROM wrong_question_analysis WHERE question_id=? AND student_id=?";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, questionId);
            ps.setLong(2, studentId);
            rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("questionId", rs.getLong("question_id"));
                m.put("analysis", rs.getString("analysis"));
                m.put("knowledgePoints", rs.getString("knowledge_points"));
                m.put("studySuggestion", rs.getString("study_suggestion"));
                return m;
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
     * 保存错误问题分析
     * @param questionId 问题 ID
     * @param studentId 学生 ID
     * @param studentAnswer 学生回答
     * @param analysis 分析内容
     * @param knowledgePoints 知识点
     * @param studySuggestion 学习建议
     */
    public void save(long questionId, long studentId, String studentAnswer,
                     String analysis, String knowledgePoints, String studySuggestion) {
        String sql = "INSERT INTO wrong_question_analysis (question_id, student_id, student_answer, analysis, knowledge_points, study_suggestion) VALUES (?,?,?,?,?,?)";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, questionId);
            ps.setLong(2, studentId);
            ps.setString(3, studentAnswer);
            ps.setString(4, analysis);
            ps.setString(5, knowledgePoints);
            ps.setString(6, studySuggestion);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }
}

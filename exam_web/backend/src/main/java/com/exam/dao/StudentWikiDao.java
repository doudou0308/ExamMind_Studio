package com.exam.dao;

import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生知识库数据访问层
 * 提供学生知识库相关的数据库操作
 */
public class StudentWikiDao {
    /**
     * 列出学生知识库
     * @param studentId 学生 ID
     * @return 学生知识库列表
     */
    public List<Map<String, Object>> listByStudent(long studentId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM student_wiki WHERE student_id=? ORDER BY mastery_level ASC, wrong_count DESC";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("conceptName", rs.getString("concept_name"));
                m.put("masteryLevel", rs.getInt("mastery_level"));
                m.put("wrongCount", rs.getInt("wrong_count"));
                m.put("totalCount", rs.getInt("total_count"));
                m.put("note", rs.getString("note"));
                m.put("lastReviewTime", rs.getString("last_review_time"));
                list.add(m);
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
     * 保存或更新学生知识库
     * @param studentId 学生 ID
     * @param conceptName 概念名称
     * @param masteryLevel 掌握等级
     * @param wrongCount 错误次数
     * @param totalCount 总次数
     * @param note 备注
     */
    public void saveOrUpdate(long studentId, String conceptName, int masteryLevel,
                              int wrongCount, int totalCount, String note) {
        String sql = "INSERT INTO student_wiki (student_id, concept_name, mastery_level, wrong_count, total_count, note) " +
                     "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                     "mastery_level=VALUES(mastery_level), wrong_count=VALUES(wrong_count), " +
                     "total_count=VALUES(total_count), note=VALUES(note), last_review_time=NOW()";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            ps.setString(2, conceptName);
            ps.setInt(3, masteryLevel);
            ps.setInt(4, wrongCount);
            ps.setInt(5, totalCount);
            ps.setString(6, note);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }

    /**
     * 清空学生知识库
     * @param studentId 学生 ID
     */
    public void clearByStudent(long studentId) {
        String sql = "DELETE FROM student_wiki WHERE student_id=?";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, studentId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }
}

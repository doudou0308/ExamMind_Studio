package com.exam.dao;

import com.exam.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 知识库数据访问层
 * 提供知识库相关的数据库操作
 * @author 你的姓名
 * @version 1.0
 * @since 2023-09-10
 * @description 提供知识库相关的数据库操作
 */




public class KnowledgeWikiDao {
    /**
     * 列出所有知识库记录
     * @return 所有知识库记录的列表
     */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM knowledge_wiki ORDER BY category";
        Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("category", rs.getString("category"));
                m.put("summary", rs.getString("summary"));
                m.put("difficultyDist", rs.getString("difficulty_dist"));
                m.put("weakQuestionIds", rs.getString("weak_question_ids"));
                m.put("relatedConcepts", rs.getString("related_concepts"));
                m.put("lastUpdated", rs.getString("last_updated"));
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
     * 保存或更新知识库记录
     * @param category 知识库分类
     * @param summary 知识库摘要
     * @param difficultyDist 知识库难度分布
     * @param weakQuestionIds 知识库弱题 ID 列表
     * @param relatedConcepts 知识库相关概念
     */
    public void saveOrUpdate(String category, String summary, String difficultyDist,
                              String weakQuestionIds, String relatedConcepts) {
        String sql = "INSERT INTO knowledge_wiki (category, summary, difficulty_dist, weak_question_ids, related_concepts) " +
                     "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                     "summary=VALUES(summary), difficulty_dist=VALUES(difficulty_dist), " +
                     "weak_question_ids=VALUES(weak_question_ids), related_concepts=VALUES(related_concepts), " +
                     "last_updated=NOW()";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, category);
            ps.setString(2, summary);
            ps.setString(3, difficultyDist);
            ps.setString(4, weakQuestionIds);
            ps.setString(5, relatedConcepts);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }



    /**
     * 清空所有知识库记录
     */
    public void clearAll() {
        String sql = "DELETE FROM knowledge_wiki";
        Connection conn = null; PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e);
        } finally {
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            DBUtil.close(conn);
        }
    }
}

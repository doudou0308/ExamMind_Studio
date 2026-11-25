package com.exam;

import com.exam.util.DBUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * 考试系统——Spring Boot 启动入口
 * <p>
 * 本类除了启动应用外，还承担了 <b>数据库自动初始化</b> 的职责：
 * <ol>
 *   <li><b>建表</b>：使用 CREATE TABLE IF NOT EXISTS 确保核心表存在</li>
 *   <li><b>种子数据</b>：首次启动时使用部署者配置的口令创建管理员账号</li>
 * </ol>
 * 核心数据表：
 * user（用户）、question（题目）、exam（考试）、
 * exam_question（考试-题目关联）、exam_record（考试记录/成绩）
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    /**
     * 应用启动后自动执行：建表 → 种子数据写入
     * <p>
     * CommandLineRunner 会在 Spring 容器启动完成后自动调用此方法，
     * 确保数据库结构在接口可用之前就已就绪。
     */
    @Bean
    public CommandLineRunner seedUsers(
            @Value("${app.bootstrap.admin-password:}") String adminPassword) {
        return new CommandLineRunner() {
            @Override
            public void run(String... args) throws Exception {
                    try (Connection conn = DBUtil.getConnection()) {
                        // ---- 第一阶段：确保所有表存在（CREATE TABLE IF NOT EXISTS） ----
                        try (Statement st = conn.createStatement()) {
                            // 建表（幂等：已存在的表不会被覆盖）
                            st.execute("CREATE TABLE IF NOT EXISTS user ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "username VARCHAR(50) NOT NULL UNIQUE,"
                        + "password VARCHAR(200) NOT NULL,"
                        + "role VARCHAR(20) NOT NULL,"
                        + "is_first_login TINYINT(1) DEFAULT 1,"
                        + "INDEX idx_role (role)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                        st.execute("CREATE TABLE IF NOT EXISTS question ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "title VARCHAR(500) NOT NULL,"
                        + "option_a VARCHAR(200),"
                        + "option_b VARCHAR(200),"
                        + "option_c VARCHAR(200),"
                        + "option_d VARCHAR(200),"
                        + "answer VARCHAR(50) NOT NULL,"
                        + "category VARCHAR(50),"
                        + "type VARCHAR(20) DEFAULT 'choice',"
                        + "difficulty INT DEFAULT 3,"
                        + "INDEX idx_category (category),"
                        + "INDEX idx_difficulty (difficulty),"
                        + "INDEX idx_type (type)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                        st.execute("CREATE TABLE IF NOT EXISTS exam ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "exam_name VARCHAR(255) NOT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "admin_id BIGINT NOT NULL,"
                        + "duration INT DEFAULT 0,"
                        + "difficulty DOUBLE DEFAULT 0,"
                        + "status VARCHAR(20) DEFAULT 'ongoing',"
                        + "INDEX idx_status (status)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                        st.execute("CREATE TABLE IF NOT EXISTS exam_question ("
                        + "exam_id BIGINT NOT NULL,"
                        + "question_id BIGINT NOT NULL,"
                        + "sort_order INT DEFAULT 0,"
                        + "score TINYINT NOT NULL DEFAULT 1,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "admin_id BIGINT DEFAULT NULL,"
                        + "is_deleted TINYINT(1) NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (exam_id, question_id),"
                        + "INDEX idx_question_id (question_id),"
                        + "INDEX idx_sort (exam_id, sort_order)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                        st.execute("CREATE TABLE IF NOT EXISTS exam_record ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "exam_id BIGINT NOT NULL,"
                        + "student_id BIGINT NOT NULL,"
                        + "score INT NOT NULL DEFAULT 0,"
                        + "answer_detail TEXT,"
                        + "submit_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE KEY uk_exam_student (exam_id, student_id),"
                        + "INDEX idx_exam_id (exam_id),"
                        + "INDEX idx_student_id (student_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                        st.execute("CREATE TABLE IF NOT EXISTS wrong_question_analysis ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "question_id BIGINT NOT NULL,"
                        + "student_id BIGINT NOT NULL,"
                        + "student_answer VARCHAR(50),"
                        + "analysis TEXT COMMENT 'AI解析内容',"
                        + "knowledge_points VARCHAR(500) COMMENT '关联知识点',"
                        + "study_suggestion TEXT COMMENT '学习建议',"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE KEY uk_q_s (question_id, student_id),"
                        + "INDEX idx_wqa_student (student_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题AI解析缓存表'");

                        st.execute("CREATE TABLE IF NOT EXISTS mastered_question ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "student_id BIGINT NOT NULL,"
                        + "question_id BIGINT NOT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE KEY uk_s_q (student_id, question_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生已掌握题目表'");

                        st.execute("CREATE TABLE IF NOT EXISTS student_wiki ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "student_id BIGINT NOT NULL,"
                        + "concept_name VARCHAR(200) NOT NULL,"
                        + "mastery_level INT DEFAULT 1 COMMENT '1-5星级',"
                        + "wrong_count INT DEFAULT 0,"
                        + "total_count INT DEFAULT 0,"
                        + "note TEXT COMMENT 'LLM学习笔记',"
                        + "last_review_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE KEY uk_s_c (student_id, concept_name)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生知识Wiki表'");

                        st.execute("CREATE TABLE IF NOT EXISTS knowledge_wiki ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "category VARCHAR(50) UNIQUE NOT NULL,"
                        + "summary TEXT COMMENT 'LLM生成的知识点总结',"
                        + "difficulty_dist TEXT COMMENT '难度分布',"
                        + "weak_question_ids TEXT COMMENT '区分度差的题目ID',"
                        + "related_concepts TEXT COMMENT '关联知识点',"
                        + "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库知识Wiki表'");
                    }

                    // ---- 第二阶段：检查是否已有管理员，有则跳过（幂等操作） ----
                    try (Statement st = conn.createStatement()) {
                        try (ResultSet rs = st.executeQuery(
                                "SELECT COUNT(*) FROM user WHERE username = 'admin'")) {
                            rs.next();
                            if (rs.getInt(1) > 0) {
                                System.out.println("用户已存在，跳过初始化");
                                return;
                            }
                        }
                    }

                    // 新数据库必须由部署者指定管理员密码，不创建固定口令或学生数据。
                    if (adminPassword == null || adminPassword.isBlank() || adminPassword.length() < 12) {
                        throw new IllegalStateException("首次启动须设置至少12位的 BOOTSTRAP_ADMIN_PASSWORD");
                    }
                    // ---- 第三阶段：仅初始化管理员（首次启动） ----
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO user (username,password,role,is_first_login) VALUES (?,?,?,?)")) {
                        ps.setString(1, "admin");
                        ps.setString(2, DigestUtils.md5DigestAsHex(adminPassword.getBytes(StandardCharsets.UTF_8)));
                        ps.setString(3, "admin");
                        ps.setBoolean(4, false);
                        ps.executeUpdate();
                    }

                    System.out.println("用户初始化完毕");
                } catch (Exception e) {
                    throw new IllegalStateException("数据库初始化失败，请检查数据库配置与首次启动密码", e);
                }
            }
        };
    }
}

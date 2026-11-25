-- ============================================
-- 题库与考试系统 数据库初始化脚本
-- 数据库名: exam_web | 字符集: utf8mb4
-- 使用方式: 在 MySQL 中执行此文件即可初始化全部表结构
-- ============================================

CREATE DATABASE IF NOT EXISTS exam_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE exam_web;

-- ============================================
-- 1. 用户表 (user)
-- ============================================
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户编号',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码（MD5加密）',
    role VARCHAR(20) NOT NULL COMMENT '角色：admin/student',
    is_first_login TINYINT(1) DEFAULT 1 COMMENT '是否首次登录',
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================
-- 2. 题目表 (question)
-- ============================================
CREATE TABLE IF NOT EXISTS question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '题目编号',
    title VARCHAR(500) NOT NULL COMMENT '题干',
    option_a VARCHAR(200) COMMENT '选项A',
    option_b VARCHAR(200) COMMENT '选项B',
    option_c VARCHAR(200) COMMENT '选项C',
    option_d VARCHAR(200) COMMENT '选项D',
    answer VARCHAR(50) NOT NULL COMMENT '正确答案',
    category VARCHAR(50) COMMENT '题目分类',
    type VARCHAR(20) DEFAULT 'choice' COMMENT '题型：choice=单选',
    difficulty INT DEFAULT 3 COMMENT '难度：1~5',
    INDEX idx_category (category),
    INDEX idx_difficulty (difficulty),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- ============================================
-- 3. 考试发布表 (exam)
-- ============================================
CREATE TABLE IF NOT EXISTS exam (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '考试编号',
    exam_name VARCHAR(255) NOT NULL COMMENT '考试名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下发时间',
    admin_id BIGINT NOT NULL COMMENT '管理员ID',
    duration INT DEFAULT 0 COMMENT '考试时长（分钟）',
    difficulty DOUBLE DEFAULT 0 COMMENT 'AI组卷平均难度1~5',
    status VARCHAR(20) DEFAULT 'ongoing' COMMENT '考试状态：ongoing=进行中, finished=已结束',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试发布表';

-- ============================================
-- 4. 考试-题目关联表 (exam_question)
-- ============================================
CREATE TABLE IF NOT EXISTS exam_question (
    exam_id BIGINT NOT NULL COMMENT '考试编号',
    question_id BIGINT NOT NULL COMMENT '题目编号',
    sort_order INT DEFAULT 0 COMMENT '题目顺序',
    score TINYINT NOT NULL DEFAULT 1 COMMENT '分值',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    admin_id BIGINT DEFAULT NULL COMMENT '操作管理员ID',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已移除',
    PRIMARY KEY (exam_id, question_id),
    INDEX idx_question_id (question_id),
    INDEX idx_sort (exam_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题目关联表';

-- ============================================
-- 5. 成绩答卷表 (exam_record)
-- ============================================
CREATE TABLE IF NOT EXISTS exam_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '答卷编号',
    exam_id BIGINT NOT NULL COMMENT '考试编号',
    student_id BIGINT NOT NULL COMMENT '学生编号',
    score INT NOT NULL DEFAULT 0 COMMENT '得分',
    answer_detail TEXT COMMENT '答题详情',
    submit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '交卷时间',
    UNIQUE KEY uk_exam_student (exam_id, student_id) COMMENT '同一学生对同一场考试只能交卷一次',
    INDEX idx_exam_id (exam_id),
    INDEX idx_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩答卷表';

-- ============================================
-- 6. 错题AI解析缓存表 (wrong_question_analysis)
-- ============================================
CREATE TABLE IF NOT EXISTS wrong_question_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号',
    question_id BIGINT NOT NULL COMMENT '题目编号',
    student_id BIGINT NOT NULL COMMENT '学生编号',
    student_answer VARCHAR(50) COMMENT '学生错误答案',
    analysis TEXT COMMENT 'AI解析内容',
    knowledge_points VARCHAR(500) COMMENT '关联知识点（逗号分隔）',
    study_suggestion TEXT COMMENT '学习建议',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_q_s (question_id, student_id) COMMENT '同一学生对同一题只缓存一次',
    INDEX idx_wqa_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题AI解析缓存表';

-- ============================================
-- 7. 学生已掌握题目表 (mastered_question)
-- ============================================
CREATE TABLE IF NOT EXISTS mastered_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号',
    student_id BIGINT NOT NULL COMMENT '学生编号',
    question_id BIGINT NOT NULL COMMENT '题目编号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '标记时间',
    UNIQUE KEY uk_s_q (student_id, question_id) COMMENT '同一学生对同一题只能标记一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生已掌握题目表';

-- ============================================
-- 8. 学生知识Wiki表 (student_wiki)
-- ============================================
CREATE TABLE IF NOT EXISTS student_wiki (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号',
    student_id BIGINT NOT NULL COMMENT '学生编号',
    concept_name VARCHAR(200) NOT NULL COMMENT '知识点名称',
    mastery_level INT DEFAULT 1 COMMENT '掌握度1-5星',
    wrong_count INT DEFAULT 0 COMMENT '错题数',
    total_count INT DEFAULT 0 COMMENT '总题数',
    note TEXT COMMENT 'LLM生成的学习笔记',
    last_review_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
    UNIQUE KEY uk_s_c (student_id, concept_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生知识Wiki表';

-- ============================================
-- 9. 题库知识Wiki表 (knowledge_wiki)
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_wiki (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号',
    category VARCHAR(50) UNIQUE NOT NULL COMMENT '知识点分类',
    summary TEXT COMMENT 'LLM生成的知识点总结',
    difficulty_dist TEXT COMMENT '难度分布描述',
    weak_question_ids TEXT COMMENT '区分度差的题目ID(逗号分隔)',
    related_concepts TEXT COMMENT '关联知识点建议',
    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库知识Wiki表';

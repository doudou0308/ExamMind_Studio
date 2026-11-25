package com.exam.model;

/**
 * 考试记录实体类 —— 对应数据库中的 exam_record 表
 * 存储学生每次参加考试的成绩和答题详情
 */
public class ExamRecord {

    /** 记录唯一标识（主键） */
    private long id;

    /** 关联的考试 ID（外键，指向 exam 表） */
    private long examId;

    /** 参加考试的学生 ID（外键，指向 user 表） */
    private long studentId;

    /** 考试得分（总分数） */
    private int score;

    /**
     * 答题详情（JSON 格式字符串）
     * 格式示例：[{"qid":1,"answer":"A","correct":true,"score":5}, ...]
     * 记录每道题的作答答案、是否正确、得分
     */
    private String answerDetail;

    /** 提交时间 */
    private String submitTime;

    /**
     * 学生姓名（非数据库字段）
     * 仅在联表查询时填充，用于页面展示，不直接存入 exam_record 表
     */
    private String studentName;

    // ======================== Getter / Setter ========================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getExamId() { return examId; }
    public void setExamId(long examId) { this.examId = examId; }
    public long getStudentId() { return studentId; }
    public void setStudentId(long studentId) { this.studentId = studentId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getAnswerDetail() { return answerDetail; }
    public void setAnswerDetail(String answerDetail) { this.answerDetail = answerDetail; }
    public String getSubmitTime() { return submitTime; }
    public void setSubmitTime(String submitTime) { this.submitTime = submitTime; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
}

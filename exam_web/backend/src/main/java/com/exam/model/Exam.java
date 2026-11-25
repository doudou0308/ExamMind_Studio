package com.exam.model;

/**
 * 考试实体类 —— 对应数据库中的 exam 表
 * 代表一次考试安排，包含考试名称、时长等基本信息
 */
public class Exam {

    /** 考试唯一标识（主键） */
    private long id;

    /** 考试名称（如"期末考试"、"期中测验"） */
    private String examName;

    /** 考试创建时间 */
    private String createTime;

    /** 创建该考试的管理员用户 ID */
    private long adminId;

    /** 考试时长（单位：分钟） */
    private int duration;

    /** 试卷平均难度（1~5，仅AI自动组卷时写入） */
    private double difficulty;

    /** 考试状态：ongoing=进行中, finished=已结束 */
    private String status;

    // ======================== Getter / Setter ========================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public long getAdminId() { return adminId; }
    public void setAdminId(long adminId) { this.adminId = adminId; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public double getDifficulty() { return difficulty; }
    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

package com.exam.model;

/**
 * 题目实体类 —— 对应数据库中的 question 表
 * 存储题库中的所有题目，包括题干、选项、正确答案、分类、题型和难度
 */
public class Question {

    /** 题目唯一标识（主键） */
    private int id;

    /** 题目内容（题干） */
    private String title;

    /** 选项 A 的内容 */
    private String optionA;

    /** 选项 B 的内容 */
    private String optionB;

    /** 选项 C 的内容 */
    private String optionC;

    /** 选项 D 的内容 */
    private String optionD;

    /** 正确答案（如 A、B、C、D 或多选组合如 AB） */
    private String answer;

    /** 题目分类（知识点/章节名称），支持模糊搜索 */
    private String category;

    /**
     * 题目类型
     * "choice" = 选择题（默认值）
     */
    private String type = "choice";

    /**
     * 难度等级（1~5）
     * 1=非常简单，3=中等（默认），5=非常困难
     * 遗传算法会根据目标平均难度来筛选题目
     */
    private int difficulty = 3;

    // ======================== Getter / Setter ========================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
}

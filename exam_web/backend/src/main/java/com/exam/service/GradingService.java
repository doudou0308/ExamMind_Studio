package com.exam.service;

import com.exam.dao.ExamDao;
import com.exam.dao.ExamRecordDao;
import com.exam.dao.QuestionDao;
import com.exam.model.Exam;
import com.exam.model.ExamRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 考试提交与判分服务层 —— 封装学生交卷的核心业务逻辑
 * <p>
 * 职责：
 * <ol>
 *   <li>防重复提交校验</li>
 *   <li>逐题比对答案并计算得分</li>
 *   <li>构建答题详情（answer_detail）</li>
 *   <li>写入考试记录（exam_record）</li>
 * </ol>
 */




@Service
public class GradingService {

    private final ExamDao examDao = new ExamDao();
    private final ExamRecordDao recordDao = new ExamRecordDao();
    private final QuestionDao questionDao = new QuestionDao();




    /**
     * 判分并提交考试
     *
     * @param examId    考试ID
     * @param studentId 学生ID
     * @param answers   学生答案 Map<题目ID, 学生答案>
     * @return 判分后的考试记录
     * @throws RuntimeException 如果重复提交或考试不存在
     */




    @Transactional(rollbackFor = Exception.class)
    public ExamRecord gradeAndSubmit(long examId, long studentId,
                                     Map<String, String> answers) {
        Exam exam = examDao.getById(examId);
        if (exam == null) {
            throw new RuntimeException("考试不存在");
        }
        if (recordDao.hasSubmitted(examId, studentId)) {
            throw new RuntimeException("您已提交过本场考试");
        }

        // 获取每题分值映射（题目ID → 分数，如 {3→2, 7→2, 12→1}）
        Map<Integer, Integer> scoreMap = examDao.listQuestionScores(examId);

        // 获取正确答案映射（题目ID → 正确答案，如 {3→"A", 7→"B", 12→"C"}）
        List<Integer> qidList = answers.keySet().stream()
                .map(Integer::parseInt).collect(java.util.stream.Collectors.toList());
        List<com.exam.model.Question> questions = questionDao.getByIds(qidList);
        Map<Integer, String> answerMap = new LinkedHashMap<>();
        for (com.exam.model.Question q : questions) {
            answerMap.put(q.getId(), q.getAnswer());
        }

        // 逐题比对判分
        int totalScore = 0;
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<String, String> e : answers.entrySet()) {
            int qid = Integer.parseInt(e.getKey());          // 题目ID
            String studentAnswer = e.getValue();              // 学生答案（如 "A" 或 ""）
            String correctAnswer = answerMap.get(qid);        // 正确答案（从数据库查的）
            if (correctAnswer != null) {
                int qScore = scoreMap.getOrDefault(qid, 1);   // 该题分值（默认1分）
                if (correctAnswer.equals(studentAnswer)) totalScore += qScore;  // 答对加分
                if (detail.length() > 0) detail.append(",");  // 用逗号分隔各题
                detail.append(qid).append(":").append(studentAnswer);  // 格式: "3:A"
            }
        }

        // 写入考试记录
        ExamRecord record = new ExamRecord();
        record.setExamId(examId);
        record.setStudentId(studentId);
        record.setScore(totalScore);
        record.setAnswerDetail(detail.toString());
        record.setSubmitTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        recordDao.add(record);

        return record;
    }
}

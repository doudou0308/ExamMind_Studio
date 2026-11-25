package com.exam.controller;

import com.exam.config.AuthContext;
import com.exam.dao.ExamDao;
import com.exam.dao.ExamRecordDao;
import com.exam.dao.QuestionDao;
import com.exam.dao.WrongQuestionAnalysisDao;
import com.exam.dao.MasteredQuestionDao;
import com.exam.dao.StudentWikiDao;
import com.exam.model.Exam;
import com.exam.model.ExamRecord;
import com.exam.model.Question;

import com.exam.service.DeepSeekService;
import com.exam.service.GradingService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端考试控制器
 * 负责：学生查看考试列表、获取试题、提交答案、查看成绩统计、错题回顾、自主练习
 */
@CrossOrigin(origins = "*")
@RestController// 标记该类为处理学生端考试请求的控制器，路径为/api/student
// 自动将返回值转换为JSON响应
@RequestMapping("/api/student")
public class StudentExamController {
   
    // 考试数据访问对象，处理考试相关的数据库操作
    private final ExamDao examDao = new ExamDao();
    // 考试记录数据访问对象，处理学生考试提交记录的数据库操作
    private final ExamRecordDao recordDao = new ExamRecordDao();
    // 试题数据访问对象，处理题目相关的数据库操作
    private final QuestionDao questionDao = new QuestionDao();
    // 错题分析数据访问对象，处理AI生成的错题解析的数据库操作
    private final WrongQuestionAnalysisDao analysisDao = new WrongQuestionAnalysisDao();
    // 已掌握题目数据访问对象，处理学生标记已掌握题目的数据库操作
    private final MasteredQuestionDao masteredDao = new MasteredQuestionDao();
    // 学生知识库数据访问对象，处理学生个人wiki的数据库操作
    private final StudentWikiDao wikiDao = new StudentWikiDao();
    // JSON序列化工具，用于对象和JSON字符串的相互转换
    private final ObjectMapper mapper = new ObjectMapper();
   
    @Autowired
    private DeepSeekService deepseek;
    @Autowired
    private GradingService gradingService;



    
    /**
     * 学生端考试列表
     * 返回所有考试，并标记当前学生是否已提交（submitted字段）
     */
    @GetMapping("/exam/list")
    public Map<String, Object> list() {//获取所有正在进行的考试
        // 获取当前登录学生的ID
        long studentId = AuthContext.getCurrentUser().getId();
        // 获取所有正在进行的考试
        List<Exam> all = examDao.listOngoing();
        // 创建最终要返回的列表，用来存放所有考试的信息
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 遍历所有正在进行的考试，逐个处理
        for (Exam e : all) {
            // 判断当前学生是否已经提交过这场考试的答案
            boolean done = recordDao.hasSubmitted(e.getId(), studentId);
            // 创建一个有序的Map，用来存放这场考试的各项信息，顺序和put的顺序一致
            Map<String, Object> examInfo = new LinkedHashMap<>();
            // 把考试的各个属性存到Map里
            examInfo.put("id", e.getId());
            examInfo.put("examName", e.getExamName());
            // 统计这场考试一共有多少道题目
            int questionNum = examDao.countQuestions(e.getId());
            examInfo.put("questionCount", questionNum);
            examInfo.put("difficulty", e.getDifficulty());
            examInfo.put("createTime", e.getCreateTime());
            // 标记学生是否已经提交过这场考试
            examInfo.put("submitted", done);
            // 把这场考试的信息添加到最终的结果列表里
            result.add(examInfo);
        }
        return Map.of("ok", true, "data", result);
    }

    /**
     * 获取某场考试的试题（不含正确答案）
     * 校验：已交卷的学生不可重复获取试题
     * 返回：题目列表（题干+选项+分类）+ 考试名称
     */
    @GetMapping("/exam/questions/{examId}")
    public Map<String, Object> questions(@PathVariable long examId) {
        long studentId = AuthContext.getCurrentUser().getId();
        if (recordDao.hasSubmitted(examId, studentId)) {
            return Map.of("ok", false, "msg", "您已完成本场考试，不可重复作答");
        }
        Exam exam = examDao.getById(examId);
        if (exam == null) return Map.of("ok", false, "msg", "考试不存在");

        // 获取考试包含的题目ID，按试卷顺序排列
        List<Integer> qids = examDao.listQuestionIds(examId);
        List<Question> questions = questionDao.getByIds(qids);
        // 不返回正确答案（防止作弊），只返回题干和选项
        // 创建一个空列表，用来存放处理后的题目信息
        List<Map<String, Object>> qs = new ArrayList<>();
        
        // 循环遍历每一道原始题目
        for (Question q : questions) {
            // 创建一个有序的Map，用来保存当前题目的信息
            Map<String, Object> m = new LinkedHashMap<>();
            // 把题目的各个属性存到Map里
            m.put("id", q.getId());
            m.put("title", q.getTitle());
            m.put("optionA", q.getOptionA());
            m.put("optionB", q.getOptionB());
            m.put("optionC", q.getOptionC());
            m.put("optionD", q.getOptionD());
            m.put("category", q.getCategory());
            // 把处理好的题目信息添加到结果列表中
            qs.add(m);
        }
        return Map.of("ok", true, "data", qs, "examName", exam.getExamName());
    }

    /**
     * 学生交卷
     * 请求体：{examId, answers: {"题目ID": "学生答案", ...}}
     *
     * 判分流程：
     * 1. 校验是否已交卷（防重复提交）
     * 2. 获取每道题的分值（scoreMap）和正确答案
     * 3. 逐题比对：正确加分，错误不计分
     * 4. 构建 answerDetail（"qid:ans,qid:ans" 格式）存入数据库
     * 5. 返回得分与总分
     */
    @PostMapping("/exam/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {

        // 从请求体中提取考试ID和学生ID
        long examId = ((Number) body.get("examId")).longValue();
        long studentId = AuthContext.getCurrentUser().getId();

        // 从请求体中提取学生答案
        @SuppressWarnings("unchecked")
        Map<String, String> answers = (Map<String, String>) body.get("answers");

        // 校验学生答案是否为空
        if (answers == null || answers.isEmpty()) {
            return Map.of("ok", false, "msg", "请先作答再交卷");
        }

        // 校验学生答案是否包含所有题目
        if (answers.size() != examDao.countQuestions(examId)) {
            return Map.of("ok", false, "msg", "请回答所有题目");
        }
        try {
            // 委托 GradingService 执行判分（防重复、逐题比对、写库）
            ExamRecord record = gradingService.gradeAndSubmit(examId, studentId, answers);
            // 计算满分：累加所有题目分值（每题默认1分，可配置）
            int maxScore = examDao.listQuestionScores(examId)
                                .values()// 提取所有题目分值
                                .stream()// 转换为流，方便进行累加操作
                                .mapToInt(Integer::intValue).sum();// 累加所有题目分值
            // 返回交卷成功信息
            return Map.of("ok", true, "score", record.getScore(),
                "total", maxScore, "msg", "交卷成功");
        } catch (RuntimeException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    /**
     * 学生个人成绩统计
     * 返回：参加考试次数、平均分、总错题数、答题正确率
     * 通过逐条记录解析 answerDetail 来统计错题
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        // 从当前登录用户获取学生ID
        long studentId = AuthContext.getCurrentUser().getId();
        List<ExamRecord> records = recordDao.listByStudentId(studentId);
        // 统计参加考试次数
        // 如果没有考试记录，返回0
        int examCount = records.size();
        // 如果没有考试记录，返回0
        if (examCount == 0) {
            // 如果没有考试记录，返回0
            // 平均分、总错题数、答题正确率都为0
            return Map.of("ok", true, "data", Map.of(
                "examCount", 0, "avgScore", 0, "wrongCount", 0, "accuracy", 0
            ));
        }
        double avgScore = records.stream().mapToInt(ExamRecord::getScore).average().orElse(0);

        // 统计所有考试的错题总数：逐条记录解析 answerDetail，比对正确答案
        // answerDetail 格式："3:A,7:B,12:C" → 拆分 → 逐题查正确答案 → 比对
        int totalWrong = records.stream().mapToInt(r -> {
            String detail = r.getAnswerDetail();
            if (detail == null || detail.isEmpty()) return 0;
            // 提取题目ID列表
            List<Integer> qids = Arrays.stream(detail.split(","))
                    .map(p -> Integer.parseInt(p.split(":")[0]))
                    .collect(Collectors.toList());
            // 查数据库获取正确答案
            List<Question> qs = questionDao.getByIds(qids);
            Map<Integer, String> correctMap = qs.stream()
                    .collect(Collectors.toMap(Question::getId, Question::getAnswer));
            // 逐题比对，统计错题数
            return (int) Arrays.stream(detail.split(",")).filter(p -> {
                String[] kv = p.split(":");
                String correct = correctMap.get(Integer.parseInt(kv[0]));
                return correct != null && !correct.equals(kv.length > 1 ? kv[1] : "");
            }).count();
        }).sum();

        // 统计总答题数（所有考试回答的题目总数）
        int totalAnswered = records.stream().mapToInt(r -> {
            // 提取 answerDetail 字符串
            // 如果为空，返回0
            String d = r.getAnswerDetail();
            // 如果为空，返回0
            return d == null || d.isEmpty() ? 0 : d.split(",").length;
        }).sum();
        int accuracy = totalAnswered > 0
                ? (int) Math.round(100.0 * (totalAnswered - totalWrong) / totalAnswered) : 0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examCount", examCount);
        data.put("avgScore", Math.round(avgScore * 10) / 10.0);
        data.put("wrongCount", totalWrong);
        data.put("accuracy", accuracy);
        return Map.of("ok", true, "data", data);
    }

    /**
     * 学生错题回顾
     * 参数：examId（可选，不传则查所有考试的错题）
     * 返回：错题列表（题目内容、学生答案、正确答案、所属考试名）
     * 使用 dedup Set 去重，同一场考试同一道题只显示一次
     */
    @GetMapping("/wrong-questions")
    // 这是一个处理GET请求的方法，请求路径是/api/student/wrong-questions
    // 用来获取学生的错题列表，参数examId是可选的，用来指定只查某场考试的错题
    public Map<String, Object> wrongQuestions(
            @RequestParam(required = false) Long examId) {
        // 先获取当前登录学生的ID，这个AuthContext里存了当前登录用户的信息
        long studentId = AuthContext.getCurrentUser().getId();
        // 调用数据库查询方法，获取这个学生所有的考试记录，每一条记录都是一次考试的作答情况
        List<ExamRecord> records = recordDao.listByStudentId(studentId);
        // 如果前端传了examId，说明要查特定考试的错题
        if (examId != null) {
            // 用stream流过滤出所有属于这个examId的考试记录，把结果转成List
            records = records.stream().filter(r -> r.getExamId() == examId)
                    .collect(Collectors.toList());
        }

        // 定义最终要返回给前端的错题列表，每个元素是一个存错题信息的Map
        List<Map<String, Object>> list = new ArrayList<>();
        // 用来给错题去重的HashSet，因为同一个学生可能在不同考试中错同一道题，我们只需要显示一次
        // 去重的key用"考试ID:题目ID"的格式拼接，保证唯一
        Set<String> dedup = new HashSet<>();  // 去重用："examId:questionId"

        // 遍历这个学生的每一条考试记录，逐个解析里面的错题
        for (ExamRecord r : records) {
            // 从考试记录里拿到学生的作答详情，这个字符串的格式是"3:A,7:B,12:C"
            // 意思是3号题选了A，7号题选了B，12号题选了C
            String detail = r.getAnswerDetail();
            // 如果作答详情是空的，说明这条记录有问题，直接跳过这条记录
            if (detail == null || detail.isEmpty()) continue;
            // 先把作答详情按逗号拆分，每一个部分是"题号:答案"，然后提取所有的题号，转成整数存到qids列表里
            List<Integer> qids = Arrays.stream(detail.split(","))
                    .map(p -> Integer.parseInt(p.split(":")[0]))
                    .collect(Collectors.toList());
            // 调用数据库查询，一次性把这些题号对应的所有题目信息查出来
            List<Question> qs = questionDao.getByIds(qids);
            // 把查询到的题目列表转成Map，key是题目ID，value是题目对象，方便后面根据ID快速找题目
            Map<Integer, Question> qMap = qs.stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            // 解析学生的答案，做成一个Map，key是题目ID，value是学生选的答案，和上面的qMap对应
            Map<Integer, String> stuAns = Arrays.stream(detail.split(","))
                    .collect(Collectors.toMap(
                            p -> Integer.parseInt(p.split(":")[0]),
                            p -> p.split(":").length > 1 ? p.split(":")[1] : ""
                    ));

            // 遍历学生所有的答案，逐个检查哪些是做错的
            for (Map.Entry<Integer, String> e : stuAns.entrySet()) {
                // 根据当前遍历到的题目ID，从qMap里拿到对应的题目对象
                Question q = qMap.get(e.getKey());
                // 如果找不到这个题目，可能是数据库里的题被删了，直接跳过
                if (q == null) continue;
                // 如果学生的答案和题目的正确答案一样，说明这道题做对了，跳过，只处理做错的
                if (q.getAnswer().equals(e.getValue())) continue; // 跳过正确题目
                // 给这道错题生成去重key，格式是"考试ID:题目ID"
                String dedupKey = r.getExamId() + ":" + e.getKey();
                // 把key加入去重的Set，如果add失败，说明这个key已经存在了，也就是这道题已经加过了，跳过
                if (!dedup.add(dedupKey)) continue; // 去重
                // 这道题是第一次出现的错题，创建一个新的Map来存这道错题的所有信息
                Map<String, Object> item = new LinkedHashMap<>();
                // 把需要的信息一个个放到Map里，顺序和put的顺序一致，LinkedHashMap会保持插入顺序
                item.put("questionId", q.getId()); // 题目ID
                item.put("title", q.getTitle()); // 题目的题干
                item.put("optionA", q.getOptionA()); // 选项A
                item.put("optionB", q.getOptionB()); // 选项B
                item.put("optionC", q.getOptionC()); // 选项C
                item.put("optionD", q.getOptionD()); // 选项D
                item.put("category", q.getCategory()); // 题目的分类（知识点）
                item.put("difficulty", q.getDifficulty()); // 题目的难度
                item.put("examName", r.getStudentName()); // 所属的考试名称（这里原代码变量名是studentName，实际存的是考试名）
                item.put("correctAnswer", q.getAnswer()); // 题目的正确答案
                item.put("studentAnswer", e.getValue()); // 学生当时选的错误答案
                item.put("submitTime", r.getSubmitTime()); // 这道题的交卷时间
                // 把这道错题加到最终要返回的列表里
                list.add(item);
            }
        }
        // 把所有处理好的错题包装成统一的返回格式，ok是true表示请求成功，data里存错题列表
        return Map.of("ok", true, "data", list);
    }

    /**
     * 最近5次考试成绩趋势（按考试顺序）
     * 返回：考试名、得分、交卷时间
     */
    @GetMapping("/score-history")
    public Map<String, Object> scoreHistory() {
        long studentId = AuthContext.getCurrentUser().getId();
        List<ExamRecord> records = recordDao.listByStudentId(studentId);
        List<Map<String, Object>> list = records.stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .limit(5)
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("examName", r.getStudentName());
                    item.put("score", r.getScore());
                    item.put("submitTime", r.getSubmitTime());
                    return item;
                }).collect(Collectors.toList());
        return Map.of("ok", true, "data", list);
    }

    /**
     * 查看某场考试的个人答题详情（含正确答案对照）
     * 路径参数：examId（考试ID）
     * 返回：每道题的题干、选项、学生答案、正确答案、是否正确
     */
    /**
     * 查看某场考试的个人答题详情（含正确答案对照）
     * 路径参数：examId（考试ID）
     * 返回：每道题的题干、选项、学生答案、正确答案、是否正确
     */
    @GetMapping("/exam/record-detail/{examId}")
    public Map<String, Object> recordDetail(@PathVariable long examId) {
        // @PathVariable的作用是把URL里的examId值提取出来，传给这个方法的参数examId
        // 如果examId是null，会自动转换为0，不会报错
        // 获取当前登录学生的ID，AuthContext是我们封装的工具类，存了当前登录用户的信息
        long studentId = AuthContext.getCurrentUser().getId();
        // 从数据库查询这场考试所有学生的考试记录，返回一个记录列表
        List<ExamRecord> records = recordDao.listByExamId(examId);
        // 用Java8的Stream流从所有记录里过滤出当前学生的记录：先过滤学生ID匹配的，再取第一个找到的，找不到就返回null
        ExamRecord record = records.stream()
                .filter(r -> r.getStudentId() == studentId)
                .findFirst().orElse(null);
        // 如果没找到记录，直接返回错误信息，告诉前端没有这个考试的记录
        if (record == null) return Map.of("ok", false, "msg", "未找到该场考试记录");

        // 从学生的考试记录里拿出作答详情，这个字段的格式是"3:A,7:B,12:C"，也就是题号:答案，用逗号分隔
        String detail = record.getAnswerDetail();
        // 处理空指针，如果作答详情是null，就把它设为空字符串，避免后面拆分字符串报错
        if (detail == null) detail = "";
        // 把字符串格式的作答详情转成Map，方便后面通过题目ID快速查学生的答案
        // Arrays.stream把拆分后的数组转成流，先过滤掉空字符串，再用collect转成Map：key是题目ID（转成整数），value是学生的答案
        Map<Integer, String> stuAns = Arrays.stream(detail.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toMap(
                        p -> Integer.parseInt(p.split(":")[0]),
                        p -> p.split(":").length > 1 ? p.split(":")[1] : ""
                ));

        // 从数据库查询这场考试所有的题目ID列表，按试卷上的顺序排列
        List<Integer> qidList = examDao.listQuestionIds(examId);
        // 一次性把所有题目ID对应的题目信息从数据库查出来，得到题目对象的列表
        List<Question> questions = questionDao.getByIds(qidList);

        // 把题目对象转成前端需要的格式，同样用Stream流处理每个题目
        List<Map<String, Object>> qs = questions.stream().map(q -> {
            // 用LinkedHashMap保证put的顺序就是返回给前端的顺序，不会乱序
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId()); // 题目ID
            m.put("title", q.getTitle()); // 题目的题干
            // 从stuAns里拿学生的答案，如果找不到就返回空字符串，避免空指针
            m.put("studentAnswer", stuAns.getOrDefault(q.getId(), ""));
            m.put("correctAnswer", q.getAnswer()); // 题目的正确答案
            // 比对学生答案和正确答案，生成一个布尔值标记这道题做对了还是做错了
            m.put("correct", q.getAnswer().equals(stuAns.get(q.getId())));
            // 把四个选项也放进去，前端可以展示题目所有选项
            m.put("optionA", q.getOptionA());
            m.put("optionB", q.getOptionB());
            m.put("optionC", q.getOptionC());
            m.put("optionD", q.getOptionD());
            return m;
        }).collect(Collectors.toList()); // 把流处理后的结果转成列表

        // 从数据库查询这场考试的基本信息，拿到考试名称
        Exam exam = examDao.getById(examId);
        // 组装最终要返回给前端的结果数据
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examName", exam != null ? exam.getExamName() : ""); // 考试名称，防止exam是空的，加个判断
        result.put("score", record.getScore()); // 学生这场考试的得分
        result.put("totalQuestion", qs.size()); // 这场考试的总题数
        result.put("questions", qs); // 所有题目的详细列表
        // 返回标准的响应格式，ok是true表示请求成功，data里存所有要返回的数据
        return Map.of("ok", true, "data", result);
    }

    /**
     * 自主练习模式
     * @param type     题型过滤（choice），可选
     * @param category 分类过滤，可选
     * @param mode     排序模式："seq"顺序 / "random"随机
     * @return { ok, data: [{id,title,optionA~D,type,difficulty,category,answer}], total }
     */
    @GetMapping("/practice")
    // @GetMapping标记这是处理自主练习请求的GET接口，三个参数都是从请求的URL参数里取的
    public Map<String, Object> practice(
            // @RequestParam表示这个参数从URL的查询参数里获取，required=false表示这个参数可以不传
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            // defaultValue是如果前端没传这个参数，就用默认值"seq"也就是顺序排列
            @RequestParam(defaultValue = "seq") String mode) {
        // 定义一个存储题目的集合pool，后面根据筛选条件往里面放题目
        List<Question> pool;
        // 如果前端传了题型参数，并且不是空字符串，就按题型查数据库的题目
        if (type != null && !type.isBlank()) {
            pool = questionDao.listByType(type);
        } else {
            // 没传题型的话，就查询数据库里所有的题目
            pool = questionDao.listAll();
        }
        // 处理知识点分类的筛选，如果前端传了分类参数，并且不是空字符串，就过滤出对应分类的题目
        if (category != null && !category.isBlank()) {
            pool = pool.stream().filter(q -> q.getCategory() != null
                    && q.getCategory().equals(category))
                    .collect(Collectors.toList());
        }
        // 如果前端要求随机排序，就调用Collections.shuffle把题目集合的顺序打乱
        if ("random".equals(mode)) Collections.shuffle(pool);
        // 如果筛选完之后pool是空的，说明没有符合条件的题目，返回错误信息
        if (pool.isEmpty()) return Map.of("ok", false, "msg", "没有符合条件的题目");

        // 把题目对象转成前端需要的格式，用Stream流处理每个题目
        List<Map<String, Object>> qs = pool.stream().map(q -> {
            // 同样用LinkedHashMap保证返回的字段顺序不乱
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId()); // 题目ID
            m.put("title", q.getTitle()); // 题干
            // 四个选项，前端可以直接展示
            m.put("optionA", q.getOptionA());
            m.put("optionB", q.getOptionB());
            m.put("optionC", q.getOptionC());
            m.put("optionD", q.getOptionD());
            m.put("type", q.getType()); // 题型
            m.put("difficulty", q.getDifficulty()); // 题目难度
            m.put("category", q.getCategory()); // 题目所属的知识点分类
            m.put("answer", q.getAnswer()); // 正确答案，自主练习模式直接给答案方便学生对照
            return m;
        }).collect(Collectors.toList());
        // 返回成功响应，data里是所有符合条件的题目，total是题目的总数
        return Map.of("ok", true, "data", qs, "total", qs.size());
    }







    /**
     * 批量分析错题（调用 DeepSeek 逐题解析，结果缓存到 wrong_question_analysis 表）
     * POST /api/student/wrong-question-analysis
     * @param body { questionIds: [1,2,3] }
     * @return { ok, data: [{questionId,title,correctAnswer,studentAnswer,analysis,knowledgePoints,studySuggestion,fromCache}] }
     */
    @PostMapping("/wrong-question-analysis")

    public Map<String, Object> analyzeWrongQuestions(@RequestBody Map<String, Object> body) {
        // 从请求体里提取 questionIds 列表
        // 如果为空，返回错误信息
        @SuppressWarnings("unchecked")
        List<Integer> qidList = (List<Integer>) body.get("questionIds");
        if (qidList == null || qidList.isEmpty()) {
            return Map.of("ok", false, 
                        "msg", "请选择要分析的错题");
        }

        // 从当前登录用户获取学生ID
        // 如果没有登录用户，返回错误信息
        long studentId = AuthContext.getCurrentUser().getId();
        List<Question> questions = questionDao.getByIds(qidList);
        if (questions.isEmpty()) return Map.of("ok", false, 
                        "msg", "未找到对应题目");

        // 创建一个空的Map，用来存储题目ID和对应的题目对象
        Map<Integer, Question> qMap = new HashMap<>();
        // 遍历所有查询到的题目，逐个存入Map
        for (Question q : questions) {
            // 用题目的ID作为key，题目对象本身作为value存入Map
            qMap.put(q.getId(), q);
        }



        // 从该学生的所有考试记录中，找到这些题的学生答案（只取第一次做错时的答案）
        List<ExamRecord> allRecords = recordDao.listByStudentId(studentId);
        Map<Integer, String> stuAnswerMap = new LinkedHashMap<>();
        // 遍历所有考试记录，逐个解析答案详情
        for (ExamRecord r : allRecords) {
                // 解析每个考试记录的答案详情，提取出每个题目的ID和答案
            String detail = r.getAnswerDetail();
            // 如果答案详情为空，跳过
            // 如果答案详情为空，跳过
            if (detail == null || detail.isEmpty()) continue;
            // 遍历每个题目的答案对，提取出题目的ID和答案
            // 如果题目的ID在请求体里，且学生没有之前做错过，就记录下这次做错的答案
            // 如果题目的ID在请求体里，且学生之前之前做错过，就跳过
            for (String pair : detail.split(",")) {
                
                String[] kv = pair.split(":");
                // 如果答案对格式错误，跳过
                if (kv.length != 2) continue;
                int qid = Integer.parseInt(kv[0]);
                String ans = kv.length > 1 ? kv[1] : "";
                // 如果答案为空，跳过
                if (ans.isEmpty()) continue;
                // 如果题目的ID在请求体里，且学生没有之前做错过，就记录下这次做错的答案
                // 如果题目的ID在请求体里，且学生之前之前做错过，就跳过
                if (qidList.contains(qid) && !stuAnswerMap.containsKey(qid)) {
                    Question q = qMap.get(qid);
                    if (q != null && !q.getAnswer().equals(ans)) {
                        stuAnswerMap.put(qid, ans);
                    }
                }
            }
        }

        // 遍历所有题目，逐个分析错题
        // 如果缓存里有，直接返回缓存结果
        // 如果缓存里没有，调用 DeepSeek 分析，结果缓存到 wrong_question_analysis 表
        // 返回成功响应，data里是所有符合条件的题目，total是题目的总数
        List<Map<String, Object>> results = new ArrayList<>();
        for (int qid : qidList) {
            // 从Map里获取题目对象
            // 如果题目对象为空，跳过
            Question q = qMap.get(qid);
            if (q == null) continue;
            // 从Map里获取学生答案
            // 如果学生答案为空，跳过
            if (stuAnswerMap.get(qid).isEmpty()) continue;
            String stuAns = stuAnswerMap.get(qid);

            // 先查缓存：该题该学生是否有过 AI 解析
            Map<String, Object> cached = analysisDao.getByQuestionAndStudent(qid, studentId);

            if (cached != null) {
                cached.put("questionId", qid);
                cached.put("title", q.getTitle());
                cached.put("correctAnswer", q.getAnswer());
                cached.put("studentAnswer", stuAns);
                cached.put("fromCache", true);  // 标记来自缓存，未重新调用AI
                results.add(cached);
                continue;
            }

            try {
                // 构造发给 AI 的提示词：包含题目、选项、正确答案、学生答案
                String prompt = String.format(
                    "你是一个专业的错题辅导老师。请分析下面这道学生做错的题目，用中文回复。\n\n" +
                    "题目：%s\nA. %s\nB. %s\nC. %s\nD. %s\n正确答案：%s\n学生答案：%s\n\n" +
                    "请严格按以下JSON格式返回（不要加markdown代码块标记）：\n" +
                    "{\"analysis\":\"详细解析，解释正确答案为什么对、学生错在哪里\",\"knowledgePoints\":\"1-3个知识点（逗号分隔）\",\"studySuggestion\":\"针对这个知识点的学习建议（1-2句话）\"}",
                    q.getTitle(),
                    q.getOptionA() != null ? q.getOptionA() : "",
                    q.getOptionB() != null ? q.getOptionB() : "",
                    q.getOptionC() != null ? q.getOptionC() : "",
                    q.getOptionD() != null ? q.getOptionD() : "",
                    q.getAnswer(),
                    stuAns.isEmpty() ? "未作答" : stuAns
                );

                // 调用 DeepSeek 分析，获取 JSON 字符串
                // 解析 JSON 字符串，提取出分析、知识点、学习建议
                String content = deepseek.call(prompt, 1024, 0.7);
                content = deepseek.extractJson(content);


                // 解析 JSON 字符串，提取出分析、知识点、学习建议
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(content, Map.class);

                // 从AI返回的解析结果中提取对应字段，提供默认空字符串防止空指针
                String analysis = (String) parsed.getOrDefault("analysis", "");
                String knowledgePoints = (String) parsed.getOrDefault("knowledgePoints", "");
                String studySuggestion = (String) parsed.getOrDefault("studySuggestion", "");
                
                // 保存分析结果到数据库
                analysisDao.save(qid, studentId, stuAns, analysis, knowledgePoints, studySuggestion);

                // 构建返回结果
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("questionId", qid);
                item.put("title", q.getTitle());
                item.put("correctAnswer", q.getAnswer());
                item.put("studentAnswer", stuAns);
                item.put("analysis", analysis);
                item.put("knowledgePoints", knowledgePoints);
                item.put("studySuggestion", studySuggestion);
                item.put("fromCache", false);
                results.add(item);

            } catch (Exception e) {
                results.add(Map.of("questionId", qid, "title", q.getTitle(),
                    "correctAnswer", q.getAnswer(), "studentAnswer", stuAns,
                    "analysis", "AI解析失败：" + e.getMessage()));
            }
        }

        return Map.of("ok", true, "data", results);
    }

    /**
     * 标记已掌握一道题
     * POST /api/student/master-question/{questionId}
     */
    @PostMapping("/master-question/{questionId}")
    public Map<String, Object> masterQuestion(@PathVariable long questionId) {
        //从当前登录用户获取获取学生 ID
        long studentId = AuthContext.getCurrentUser().getId();
        // 标记已掌握
        // 保存到数据库
        // 返回成功
        masteredDao.mark(studentId, questionId);
        return Map.of("ok", true);
    }

    
    /**
     * 取消已掌握标记
     * DELETE /api/student/master-question/{questionId}
     */
    @DeleteMapping("/master-question/{questionId}")
    public Map<String, Object> unmasterQuestion(@PathVariable long questionId) {
        long studentId = AuthContext.getCurrentUser().getId();// 从当前登录用户获取获取学生 ID
        masteredDao.unmark(studentId, questionId);// 取消学生对题目 questionId 的掌握标记
        return Map.of("ok", true);
    }

    /**
     * 获取已掌握题目 ID 列表
     * GET /api/student/mastered-questions
     * @return { ok, data: [1, 2, 3] }
     */
    @GetMapping("/mastered-questions")
    public Map<String, Object> listMasteredQuestions() {
        long studentId = AuthContext.getCurrentUser().getId();
        return Map.of("ok", true, "data", masteredDao.listMastered(studentId));
    }




    /**
     * 获取个人知识图谱（从 student_wiki 表读取缓存）
     * GET /api/student/knowledge-map
     * @return { ok, data: [{conceptName,masteryLevel,wrongCount,totalCount,note,lastReviewTime}] }
     */
    @GetMapping("/knowledge-map")
    public Map<String, Object> knowledgeMap() {
        long studentId = AuthContext.getCurrentUser().getId();
        List<Map<String, Object>> wiki = wikiDao.listByStudent(studentId);// 从数据库读取学生个人wiki
        if (wiki.isEmpty()) {
            return Map.of("ok", true, "data", List.of(), "msg", "尚未生成知识图谱，请先点击[重建知识图谱]");
        }
        return Map.of("ok", true, "data", wiki);
    }



    /**
     * 重建个人知识图谱（DeepSeek 分析全部考试记录 → 写入 student_wiki）
     * POST /api/student/rebuild-knowledge-map
     * @return { ok, data: [{conceptName,masteryLevel,wrongCount,totalCount,note}] }
     */
    @PostMapping("/rebuild-knowledge-map")
    public Map<String, Object> rebuildKnowledgeMap() {
        long studentId = AuthContext.getCurrentUser().getId();// 从当前登录用户获取获取学生 ID
        List<ExamRecord> records = recordDao.listByStudentId(studentId);// 从数据库读取学生考试记录
        if (records.isEmpty()) 
            return Map.of("ok", false, 
        "msg", "还没有考试记录");

        // 统计各知识点的答题总数和错题数（按题目分类归类）
        Map<String, Integer> catWrong = new LinkedHashMap<>();  // 分类 → 错题数
        Map<String, Integer> catTotal = new LinkedHashMap<>();  // 分类 → 答题总数

        for (ExamRecord r : records) {
            String detail = r.getAnswerDetail();
            if (detail == null || detail.isEmpty()) 
                continue;
            for (String pair : detail.split(",")) {
                // 解析每个题目答案对
                String[] kv = pair.split(":");
                int qid = Integer.parseInt(kv[0]);
                String ans = kv.length > 1 ? kv[1] : "";
                Question q = questionDao.getById(qid);// 从数据库读取题目



                // 如果题目不存在，跳过该对题目
                if (q == null) continue;
                String cat = q.getCategory() != null ? q.getCategory() : "未分类";
                catTotal.merge(cat, 1, Integer::sum);     // 累加答题数
                // 如果答案错误，累加错题数
                if (!q.getAnswer().equals(ans)) {
                    catWrong.merge(cat, 1, Integer::sum); // 累加错题数
                }
            }
        }

        if (catTotal.isEmpty()) return Map.of("ok", false, "msg", "没有可分析的数据");

        try {
            // 把统计数据拼成文字，发给 AI 分析
            StringBuilder catInfo = new StringBuilder();
            for (String cat : catTotal.keySet()) {
                int total = catTotal.getOrDefault(cat, 0);
                int wrong = catWrong.getOrDefault(cat, 0);
                double accuracy = total > 0 ? (total - wrong) * 100.0 / total : 100;
                catInfo.append(cat)
                .append(": 共")
                .append(total)
                .append("题 错")
                .append(wrong)
                .append("题 正确率").append(Math.round(accuracy)).append("%\n");
            }

            String prompt = "你是一个学习分析专家。请根据以下学生的答题数据，生成知识图谱。\n\n" +
                "各知识点统计：\n" + catInfo.toString() + "\n" +
                "请严格按以下JSON格式返回，只返回JSON，不要加任何解释或markdown标记：\n" +
                "{\"concepts\":[{\"name\":\"知识点名\",\"masteryLevel\":3,\"note\":\"1-2句学习建议\"}]}\n" +
                "masteryLevel规则：正确率>=90%→5, >=70%→4, >=50%→3, >=30%→2, <30%→1";


            String content = deepseek.call(prompt, 2048, 0.5);
            content = deepseek.extractJson(content);


            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(content, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> concepts = (List<Map<String, Object>>) parsed.get("concepts");

            if (concepts == null || concepts.isEmpty()) {
                return Map.of("ok", false, "msg", "AI未能识别出有效知识点，请确认题库中有分类标记");
            }

            // 清空旧数据，写入 AI 生成的新知识图谱
            wikiDao.clearByStudent(studentId);
            for (Map<String, Object> c : concepts) {
                String name = (String) c.get("name");
                int level = c.get("masteryLevel") instanceof Number n ? n.intValue() : 3;
                String note = (String) c.getOrDefault("note", "");
                int wrong = catWrong.getOrDefault(name, 0);
                int total = catTotal.getOrDefault(name, 0);
                wikiDao.saveOrUpdate(studentId, name, level, wrong, total, note);
            }

            return Map.of("ok", true, "data", wikiDao.listByStudent(studentId));
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "生成失败：" + e.getMessage());
        }
    }

    /**
     * 生成学习周报（DeepSeek 基于知识图谱 + 最近成绩生成学习建议）
     * GET /api/student/weekly-report
     * @return { ok, data: {summary,weakPoints,strengths,suggestion} }
     */
    @GetMapping("/weekly-report")
    public Map<String, Object> weeklyReport() {
        long studentId = AuthContext.getCurrentUser().getId();
        List<Map<String, Object>> wiki = wikiDao.listByStudent(studentId);

        // 最近一次考试成绩（records 按时间倒序，第一条即最新）
        List<ExamRecord> records = recordDao.listByStudentId(studentId);
        int recentScore = records.isEmpty() ? 0 : records.get(0).getScore();

        try {
            // 拼接知识图谱数据给 AI
            StringBuilder wikiInfo = new StringBuilder();
            for (Map<String, Object> w : wiki) {
                wikiInfo.append("- ").append(w.get("conceptName")).append(": 掌握度")
                       .append(w.get("masteryLevel")).append("/5, 错")
                       .append(w.get("wrongCount")).append("/").append(w.get("totalCount")).append("题\n");
            }


            // 构建提示词
            String prompt = "你是一个学习辅导老师。请根据学生知识图谱生成周报。\n\n" +
                "知识图谱：\n" + wikiInfo.toString() + "\n最近一次考试得分：" + recentScore + "\n\n" +
                "请严格按JSON返回，只返回JSON，不要加任何解释或markdown标记：\n" +
                "{\"summary\":\"总体评价1-2句\",\"weakPoints\":[\"薄弱点1\",\"薄弱点2\"],\"strengths\":[\"强项1\"],\"suggestion\":\"下周学习建议\"}";

            
            // 调用 DeepSeek 生成周报
            String content = deepseek.call(prompt, 1024, 0.5);
            content = deepseek.extractJson(content);
            @SuppressWarnings("unchecked")



            // 解析 JSON 字符串为 Map<String, Object>
            // 这里假设 JSON 字符串符合预期格式，否则会抛出异常
            Map<String, Object> report = mapper.readValue(content, Map.class);



            return Map.of("ok", true, "data", report);
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "生成失败：" + e.getMessage());
        }
    }
}

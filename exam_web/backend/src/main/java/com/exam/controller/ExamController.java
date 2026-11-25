package com.exam.controller;

import com.exam.config.AuthContext;
import com.exam.dao.ExamDao;
import com.exam.dao.ExamRecordDao;
import com.exam.dao.QuestionDao;
import com.exam.model.Exam;
import com.exam.model.ExamRecord;
import com.exam.model.Question;
import com.exam.util.GeneticAlgorithm;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试管理控制器（管理员端）
 * 负责：创建/删除考试、手动/自动组卷、查看成绩记录、题目区分度分析
 */
@CrossOrigin(origins = "*")
@RestController//每个方法都返回 JSON 格式
@RequestMapping("/api/admin/exam")

public class ExamController {
    private final ExamDao examDao = new ExamDao();
    private final ExamRecordDao recordDao = new ExamRecordDao();
    private final QuestionDao questionDao = new QuestionDao();

    /**
     * 手动创建考试（管理员自选题目）
     * 请求体：{examName, questionIds, duration}
     * 流程：校验参数 → 创建Exam记录 → 绑定题目关联 → 返回成功
     * 使用事务保证考试与题目绑定的一致性
     */
    @PostMapping("/create")
    @Transactional//开启事务
    @ResponseBody//返回 JSON 格式
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        // 校验请求体参数
        String name = (String) body.get("examName");
        @SuppressWarnings("unchecked")
        // 强制类型转换
        List<Integer> ids = (List<Integer>) body.get("questionIds");
        // 校验考试时长
        Integer duration = body.get("duration") instanceof Number n ? n.intValue() : 0;
        // 校验考试名称和题目列表是否为空
        // 校验考试名称是否为空
        if (name == null || name.isBlank()) 
            return Map.of("ok", false, 
                          "msg", "考试名称不能为空");
        // 校验题目列表是否为空
        if (ids == null || ids.isEmpty()) 
            return Map.of("ok", false, 
                          "msg", "请选择题目");

        // 创建考试记录
        Exam exam = new Exam();
        exam.setExamName(name);

        // 设置创建该考试的管理员用户 ID，方便后期加入教师关联
        exam.setAdminId(AuthContext.getCurrentUser().getId());
        // 设置考试时长（单位：分钟）
        exam.setDuration(duration);
        //获取创建的考试ID
        long examId = examDao.create(exam);
        // 绑定题目关联到考试记录
        examDao.insertExamQuestions(examId, ids, exam.getAdminId());
        return Map.of("ok", true, 
                      "msg", "考试下发成功");
    }

    /**
     * 分页查询考试列表
     * 参数：page(页码), pageSize(每页条数)
     * 返回：考试基本信息 + 每题题目数量 + 创建时间
     */
    @GetMapping("/list")
    //从url参数中获取page和pageSize，默认值为1和10
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) 
            {

         // 校验页码和每页条数是否有效
        if (page < 1) 
                page = 1;

        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 数据库查询分页查询考试列表
        List<Exam> exams = examDao.listPaged(offset, pageSize);
        int total = examDao.count();
        // 映射为 JSON 格式
        List<Map<String, Object>> result = new ArrayList<>();
        // 遍历考试列表，将每个考试的题目数量添加到 JSON 格式中
        for (int i = 0; i < exams.size(); i++) {
            // 从数据库查询每个考试的题目数量
            Exam e = exams.get(i);
            // 用 LinkedHashMap 是为了保证返回给前端的 JSON 里，字段顺序和 put 的顺序一致
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("examName", e.getExamName());
            m.put("questionCount", examDao.countQuestions(e.getId()));
            m.put("duration", e.getDuration());
            m.put("difficulty", e.getDifficulty());
            m.put("createTime", e.getCreateTime());
            m.put("status", e.getStatus() != null ? e.getStatus() : "ongoing");
            result.add(m);
        }
        
        return Map.of("ok", true, 
                        "data", result, 
                        "total", total, 
                        "page", page, 
                        "pageSize", pageSize);
        /*
        {
            "ok": true,
              "data": [
                  {"id": 1, "examName": "期中考试", ...},
                  {"id": 2, "examName": "期末模拟", ...}
                 ],
                "total": 15,
                "page": 1,
                "pageSize": 10
}
        */
    }



    
    /**
     * 分页查询指定考试的全部学生成绩记录
     * 路径参数：examId - 目标考试的唯一标识ID
     * 接口返回字段说明：
     * - studentId：学生的唯一用户ID
     * - studentName：学生姓名（兼容历史数据，无姓名时显示"未知"）
     * - score：该学生在本次考试的最终得分
     * - submitTime：学生的交卷时间
     */
    @GetMapping("/records/{examId}")
    public Map<String, Object> records(
            @PathVariable long examId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
                // 校验页码和每页条数是否有效
        if (page < 1) 
            page = 1;
        int offset = (page - 1) * pageSize;
        // 从数据库查询分页查询成绩记录
        List<ExamRecord> all = recordDao.listByExamIdWithStudentName(examId);
        // 计算分页偏移量
        // min(10+10, 13) = 13
        int toIdx = Math.min(offset + pageSize, all.size());
        // 分页查询成绩记录
        List<ExamRecord> paged = offset < all.size() ? all.subList(offset, toIdx) : List.of();
        // 映射为 JSON 格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < paged.size(); i++) {
            ExamRecord r = paged.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("studentName", r.getStudentName() != null ? r.getStudentName() : "未知");
            m.put("score", r.getScore());
            m.put("submitTime", r.getSubmitTime());
            result.add(m);
        }
        // 返回 JSON 格式
        return Map.of("ok", true, 
                      "data", result, 
                      "total", all.size(), 
                      "page", page, 
                      "pageSize", pageSize);
    }

    /**
     * 查看某个学生的答题详情（逐题对照）
     * 路径参数：recordId（成绩记录ID）
     * 返回：每道题的题干、学生答案、正确答案、是否正确
     * 数据格式：answerDetail 存储为 "题目ID:学生答案,题目ID:学生答案" 的逗号分隔串
     */
    @GetMapping("/detail/{recordId}")
    // 校验路径参数是否为数字
    public Map<String, Object> detail(@PathVariable long recordId) {

        ExamRecord record = recordDao.getById(recordId);
        // 校验记录是否存在
        if (record == null) 
            return Map.of("ok", false, 
                                          "msg", "记录不存在");

        // 解析答题详情："qid1:ans1,qid2:ans2" → 提取题目ID列表和学生答案映射
        String[] pairs = record.getAnswerDetail().split(",");
        /**
         * 数据库里存的 answerDetail:
            "3:A,7:B,12:C"
                ↓ split(",") 切成数组
                pairs = ["3:A", "7:B", "12:C"]
                ↓ 循环每个元素，split(":") 切成两半
                第1轮: p="3:A"  → kv=["3","A"] → qid=3, ans="A" → qidList加3, stuAnsMap加{3:"A"}
                第2轮: p="7:B"  → kv=["7","B"] → qid=7, ans="B" → qidList加7, stuAnsMap加{7:"B"}
                第3轮: p="12:C" → kv=["12","C"] → qid=12, ans="C" → qidList加12, stuAnsMap加{12:"C"}
                结果:
                qidList = [3, 7, 12]
                stuAnsMap = {3:"A", 7:"B", 12:"C"}
         */
        List<Integer> qidList = new ArrayList<>();
        Map<Integer, String> stuAnsMap = new LinkedHashMap<>();
        // 解析每个元素，提取题目ID和学生答案
        for (String p : pairs) {
            //按":"切分
            //"3:A,7:B,12:"
            String[] kv = p.split(":"); // kv = ["3", "A"]
            int qid = Integer.parseInt(kv[0]);// qid = 3
            
            // 处理可能的空答案情况
            String ans;
            if (kv.length > 1) {// kv.length = 2，大于 1
                ans = kv[1];// kv[1] = "A"
            } else {
                ans = "";
            }
                qidList.add(qid);// qidList = [3]
                stuAnsMap.put(qid, ans);    // stuAnsMap = {3 → "A"}
        }
        // 从数据库查询题目详情
        List<Question> questions = questionDao.getByIds(qidList);
        Map<Integer, Question> qMap = new LinkedHashMap<>();
        // 将题目列表转换为映射，键为题目ID
        // 例如：{3:题目3, 7:题目7, 12:题目12}
        for (Question q : questions) {
            qMap.put(q.getId(), q);
        }

        // 逐题组装答题详情：学生答案 vs 正确答案
        List<Map<String, Object>> details = new ArrayList<>();
        // 遍历题目ID列表，组装答题详情
        // 例如：{questionId:3, title:"A", studentAnswer:"A", correctAnswer:"A", correct:true}
        // 例如：{questionId:7, title:"B", studentAnswer:"B", correctAnswer:"B", correct:true}
        // 例如：{questionId:12, title:"C", studentAnswer:"C", correctAnswer:"C", correct:true}
        // 例如：{questionId:12, title:"C", studentAnswer:"", correctAnswer:"", correct:false}
        for (int i = 0; i < qidList.size(); i++) {
            int qid = qidList.get(i);
            Question q = qMap.get(qid);
            String studentAnswer = stuAnsMap.get(qid);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("questionId", qid);
            d.put("title", q != null ? q.getTitle() : "题目已删除");
            d.put("studentAnswer", studentAnswer);
            d.put("correctAnswer", q != null ? q.getAnswer() : "");
            d.put("correct", q != null && q.getAnswer().equals(studentAnswer));
            details.add(d);
        }

        // 组装 JSON 格式
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", record.getScore());
        result.put("submitTime", record.getSubmitTime());
        result.put("details", details);
        return Map.of("ok", true, 
                        "data", result);
    }

    /**
     * 自动组卷（支持遗传算法和难度均匀分布两种策略）
     * 请求体：{examName考试名, questionCount题目数量, 
     *          category分类分类, difficulty难度, 
     *          duration考试时长, useGA是否使用遗传算法}
     *
     * 两种组卷策略：
     * 1. 遗传算法（useGA=true 且题库≥需求3倍）：按目标难度和分类分布演化出最优题目组合
     * 2. 难度均匀法（默认）：将题库按难度排序后均匀采样，保证难度分布均衡
     */
    @PostMapping("/auto-generate")
    @Transactional
    public Map<String, Object> autoGenerate(@RequestBody Map<String, Object> body) {
        // 从请求体中提取考试参数
        String name = (String) body.get("examName");
        Integer count = body.get("questionCount") instanceof Number n ? n.intValue() : null;
        String category = (String) body.get("category");
        Integer difficulty = body.get("difficulty") instanceof Number n ? n.intValue() : null;
        Integer duration = body.get("duration") instanceof Number n ? n.intValue() : 0;
        Boolean useGA = body.get("useGA") instanceof Boolean b ? b : false;

        // 校验请求参数是否完整
        if (name == null || name.isBlank()) return Map.of("ok", false, 
                        "msg", "考试名称不能为空");
        if (count == null || count <= 0) return Map.of("ok", false, 
                        "msg", "题目数量必须大于0");

         // 从数据库查询所有题目
         // 例如：{3:题目3, 7:题目7, 12:题目12}
        List<Question> all = questionDao.listAll();
        List<Question> pool = all.stream()
                // 第一道筛子：按分类过滤（未指定分类则全部保留）
                .filter(q -> category == null || category.isBlank()
                        || q.getCategory().equals(category))
                // 第二道筛子：按难度过滤（未指定难度则全部保留）
                .filter(q -> difficulty == null || q.getDifficulty() == difficulty)
                .collect(Collectors.toList());

        if (pool.isEmpty()) return Map.of("ok", false, 
                        "msg", String.format("没有符合条件的题目，请先添加题目"));

        if (pool.size() < count) return Map.of("ok", false, 
                        "msg", String.format("符合条件的题目只有%d道，不足%d道", pool.size(), count));



                        
        // === 核心：选择组卷策略 ===
        List<Integer> selectedIds;
        if (useGA && pool.size() >= count * 3) {
            // 策略A：遗传算法 — 当题库足够大时启用，追求难度和分类的最优分布
            // 计算当前符合条件题库的平均难度，作为遗传算法的目标平均难度
            // 如果无法计算平均值（比如题库为空，理论上前面已校验不会发生），默认用3（中等难度）

            double targetAvg = pool.stream()// 提取所有题目的难度值
                                   .mapToInt(Question::getDifficulty)// 提取所有题目的难度值
                                   .average()// 计算平均值
                                   .orElse(3);// 如果无法计算平均值，返回3
        
            Set<String> targetCats = pool.stream()// 提取所有题目的分类
                                   .map(Question::getCategory)// 提取所有题目的分类
                                   .filter(Objects::nonNull)// 过滤掉 null 值
                                   .collect(Collectors.toSet());// 提取所有不同的分类
        
            selectedIds = GeneticAlgorithm.evolve(pool, count, targetAvg, targetCats);// 运行遗传算法，返回最优中的题目ID列表

        } else {



        
            // 策略B：难度均匀法 — 按难度排序后等步长采样，保证从易到难均匀覆盖======================================================================
            Collections.shuffle(pool);
            // 按难度排序
            Collections.sort(pool, Comparator.comparingInt(Question::getDifficulty));// 按难度排序，确保从易到难

            // 按难度排序后等步长采样，保证从易到难均匀覆盖
            List<Question> selected = diffSelect(pool, count);


            // 提取选中的题目ID列表
            selectedIds = selected.stream().map(Question::getId).collect(Collectors.toList());
        }

        // 创建考试并绑定选中的题目
        Exam exam = new Exam();
        exam.setExamName(name);
        exam.setAdminId(AuthContext.getCurrentUser().getId());
        exam.setDuration(duration);
 
        // 计算选中题目的平均难度
        int sumDifficulty = 0; // 存储所有选中题目的难度总和
        int selectedCount = 0; // 记录选中的题目数量
        // 遍历所有符合条件的题目池
        for (Question q : pool) {
            // 判断当前题目是否在选中的题目ID列表中
            if (selectedIds.contains(q.getId())) {
                // 累加难度值
                sumDifficulty += q.getDifficulty();
                // 选中题目数量加1
                selectedCount++;
            }
        }
        // 计算平均难度，如果没有选中题目默认使用3（中等难度）
        double avgDiff;
        if (selectedCount > 0) {
            avgDiff = (double) sumDifficulty / selectedCount;
        } else {
            avgDiff = 3.0;
        }
        // 将平均难度保留一位小数，存入考试对象
        exam.setDifficulty(Math.round(avgDiff * 10) / 10.0);
        // 创建考试记录，获取生成的考试ID
        long examId = examDao.create(exam);
        // 绑定考试和选中的题目关联关系
        examDao.insertExamQuestions(examId, selectedIds, exam.getAdminId());

        return Map.of("ok", true, 
                        "msg", String.format("自动组卷成功（平均难度%.1f）", avgDiff));
    }

    // 难度均匀采样算法
    // 将已按难度排序的题库等步长取 count 个题目（取每个区间的中点），保证难度均匀分布
    private List<Question> diffSelect(List<Question> sorted, int count) {
        // 按难度排序后等步长取 count 个题目（取每个区间的中点），保证难度均匀分布
        List<Question> result = new ArrayList<>();
        if (sorted.isEmpty()) return result;
        double step = (double) sorted.size() / count;
        for (int i = 0; i < count; i++) {
            int idx = (int) (i * step + step / 2);
            if (idx >= sorted.size()) idx = sorted.size() - 1;
            result.add(sorted.get(idx));
        }
        return result;
    }

    /**
     * 题目区分度分析
     * 路径参数：examId（考试ID）
     *
     * 计算方法：
     * 1. 将学生按总分降序排列，取前1/3为高分组、后1/3为低分组
     * 2. 对每道题计算区分度 = (高分组正确数 - 低分组正确数) / 分组人数
     * 3. 区分度 ≥0.4 为"优秀"，≥0.2 为"良好"，否则"需改进"
     * 要求至少4份答卷才可计算（保证分组有意义）
     */
    @GetMapping("/discrimination/{examId}")
    public Map<String, Object> discrimination(@PathVariable long examId) {
        List<ExamRecord> records = recordDao.listByExamId(examId);   // 查出这场考试的全部答卷
        
        if (records.size() < 4) return Map.of("ok", false,            // 至少4份才够分三组
                                              "msg", "答卷数量不足4份，无法计算区分度");

        List<Integer> qids = examDao.listQuestionIds(examId);         // 考试包含的题目ID列表
        List<Question> questions = questionDao.getByIds(qids);        // 查出完整题目对象

        // 按总分从高到低排序 → 取前1/3为高分组，后1/3为低分组（中间1/3不用）
        records.sort(Comparator.comparingInt(ExamRecord::getScore).reversed());
        int split = records.size() / 3;                               // 每组人数，如21人→每组7人
        List<ExamRecord> high = records.subList(0, split);             // 前7人 = 高分组
        List<ExamRecord> low = records.subList(records.size() - split, records.size());  // 后7人 = 低分组

        // 逐题计算区分度：高分组正确率 - 低分组正确率
        List<Map<String, Object>> list = questions.stream().map(q -> {
            long highCorrect = high.stream().filter(r -> {             // 高分组里多少人答对这题
                String ans = parseAnswer(r.getAnswerDetail(), q.getId());
                return q.getAnswer().equals(ans);
            }).count();
            long lowCorrect = low.stream().filter(r -> {              // 低分组里多少人答对这题
                String ans = parseAnswer(r.getAnswerDetail(), q.getId());
                return q.getAnswer().equals(ans);
            }).count();
            double disc = (double) (highCorrect - lowCorrect) / split; // 区分度 = 高正确率 - 低正确率

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", q.getId());
            item.put("title", q.getTitle());
            item.put("discrimination", Math.round(disc * 100) / 100.0); // 保留两位小数
            item.put("quality", disc >= 0.4 ? "优秀" : disc >= 0.2 ? "良好" : "需改进");  // 区分度评级
            return item;
        }).collect(Collectors.toList());

        return Map.of("ok", true, "data", list);
    }

    /**
     * 从答题详情字符串中解析某道题的学生答案
     * @param detail 答题详情字符串，格式为"题目ID:答案,题目ID:答案"
     * @param qid 要查询答案的题目ID
     * @return 该题目的学生答案，若不存在则返回空字符串
     */
    private String parseAnswer(String detail, int qid) {
        if (detail == null || detail.isEmpty()) return "";
        return Arrays.stream(detail.split(","))
                .map(p -> p.split(":"))
                .filter(kv -> Integer.parseInt(kv[0]) == qid)
                .map(kv -> kv.length > 1 ? kv[1] : "")
                .findFirst().orElse("");
    }

    /**
     * 结束考试（将状态改为 finished）
     * 路径参数：id（考试ID）
     */
    @PutMapping("/finish/{id}")
    public Map<String, Object> finish(@PathVariable long id) {
        Exam exam = examDao.getById(id);
        if (exam == null) return Map.of("ok", false, 
                                        "msg", "考试不存在");
        examDao.finish(id);
        return Map.of("ok", true, 
                        "msg", "考试已结束");   
    }

    /**
     * 删除考试（含关联题目绑定、所有成绩记录）
     * 路径参数：id（考试ID）
     * 使用事务保证三个删除操作的原子性
     */
    @DeleteMapping("/delete/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable long id) {
        examDao.deleteExamQuestions(id);
        recordDao.deleteByExamId(id);
        examDao.delete(id);
        return Map.of("ok", true, 
                        "msg", "考试已删除");
    }
}

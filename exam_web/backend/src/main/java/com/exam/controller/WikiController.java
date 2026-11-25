package com.exam.controller;

import com.exam.dao.ExamRecordDao;
import com.exam.dao.KnowledgeWikiDao;
import com.exam.dao.QuestionDao;
import com.exam.model.ExamRecord;
import com.exam.model.Question;
import com.exam.service.DeepSeekService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;


// 开启跨域支持，允许所有来源访问
@CrossOrigin(origins = "*")
// 标记该类为Spring MVC的REST控制器，自动将返回值转换为JSON响应
@RestController
// 统一配置该控制器下所有接口的根请求路径，即所有接口都以/api/admin/wiki开头
@RequestMapping("/api/admin/wiki")
public class WikiController {

    private final KnowledgeWikiDao wikiDao = new KnowledgeWikiDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ExamRecordDao recordDao = new ExamRecordDao();
    private final ObjectMapper mapper = new ObjectMapper();


    @Autowired
    private DeepSeekService deepseek;

    /**
     * 获取题库知识 Wiki 列表
     * GET /api/admin/wiki/list
     * @return { ok, data: [{category, summary, difficultyDist, weakQuestionIds, relatedConcepts, lastUpdated}] }
     */
    @GetMapping("/list")
    // 标记该方法为处理GET请求的处理程序，返回值为Map<String, Object>
    // 自动将返回值转换为JSON响应



    // 从数据库中查询所有知识库记录
    public Map<String, Object> list() {
        try {
            return Map.of("ok", true, "data", wikiDao.listAll());
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "读取Wiki失败：" + e.getMessage());
        }
    }



    /**
     * 重建题库知识 Wiki（调用 DeepSeek 分析全部题目和考试记录）
     * POST /api/admin/wiki/rebuild
     * @return { ok, data: [...] }
     */
    @PostMapping("/rebuild")
    // 标记该方法为处理POST请求的处理程序，返回值为Map<String, Object>
    // 自动将返回值转换为JSON响应



    // 从数据库中查询所有题目
    public Map<String, Object> rebuild() {
        List<Question> allQ = questionDao.listAll();
        if (allQ.isEmpty()) return Map.of("ok", false, "msg", "题库为空");

        // 按分类分组题目
        Map<String, List<Question>> byCat = new LinkedHashMap<>();
        for (Question q : allQ) {
            String cat = q.getCategory() != null ? q.getCategory() : "未分类";


            // 检查当前分类是否已经在分组Map中存在
            if (!byCat.containsKey(cat)) {
                // 如果不存在，就为这个分类创建一个新的空列表
                byCat.put(cat, new ArrayList<>());
            }
            // 将当前题目添加到对应分类的列表中
            byCat.get(cat).add(q);
        }

        
        // 从数据库中查询所有考试记录
        List<ExamRecord> allRecords = recordDao.listAll();
        // 初始化判别矩阵，每个题目有3个维度：总次数、正确次数、错误次数
        Map<Integer, double[]> discriminationMap = new LinkedHashMap<>();



        // 初始化判别矩阵，每个题目有3个维度：总次数、正确次数、错误次数
        for (Question q : allQ) {
            discriminationMap.put(q.getId(), new double[]{0.0, 0.0, 0.0});
        }



        // 遍历所有考试记录，统计每个题目的正确次数和错误次数
        for (ExamRecord r : allRecords) {
            String detail = r.getAnswerDetail();

            // 校验考试记录是否包含答案详情
            if (detail == null || detail.isEmpty()) continue;
            // 遍历每个题目的答案详情
            for (String pair : detail.split(",")) {

                // 解析每个题目的答案详情
                String[] kv = pair.split(":");
                int qid = Integer.parseInt(kv[0]);
                String ans = kv.length > 1 ? kv[1] : "";
                Question q = questionDao.getById(qid);

                // 校验题目是否存在
                if (q == null) continue;
                double[] d = discriminationMap.get(qid);
                // 校验判别矩阵是否存在
                if (d == null) d = new double[]{0.0, 0.0, 0.0};
                d[0]++;
                // 校验答案是否正确
                if (q.getAnswer().equals(ans)) d[1]++;
                else d[2]++;
                discriminationMap.put(qid, d);
            }
        }



        // 构建知识库摘要
        StringBuilder allInfo = new StringBuilder();
        for (Map.Entry<String, List<Question>> e : byCat.entrySet()) {
            String cat = e.getKey();
            List<Question> qs = e.getValue();
            double avgDiff = qs.stream().mapToInt(Question::getDifficulty).average().orElse(0);
            allInfo.append(cat).append(": ").append(qs.size()).append("题, 平均难度")
                   .append(String.format("%.1f", avgDiff)).append("\n");
            for (Question q : qs) {
                double[] d = discriminationMap.get(q.getId());
                double correctRate = d != null && d[0] > 0 ? d[1] / d[0] : 0;
                allInfo.append("  - [ID:").append(q.getId()).append("] ").append(q.getTitle())
                       .append(" (难度").append(q.getDifficulty()).append(", 正确率")
                       .append(String.format("%.0f%%", correctRate * 100)).append(")\n");
            }
        }



        // 调用 DeepSeek 分析知识库摘要
        try {
            String prompt = "你是一个题库分析专家。请分析以下题库数据，生成知识Wiki。\n\n" +
                "题库数据：\n" + allInfo.toString() + "\n" +
                "请严格按JSON返回（不要markdown标记）：\n" +
                "{\"categories\":[{\"category\":\"分类名\",\"summary\":\"该分类知识覆盖总结1-2句\"," +
                "\"difficultyDist\":\"难度分布描述\",\"qualityIssues\":\"质量有问题的题目ID(逗号分隔,无不填)\"," +
                "\"relatedConcepts\":\"关联知识点建议\"}]}";

            String content = deepseek.call(prompt, 4096, 0.5);
            content = deepseek.extractJson(content);


            // 解析 JSON 字符串
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(content, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> categories = (List<Map<String, Object>>) parsed.get("categories");


            // 校验 JSON 字符串是否包含 categories 字段
            if (categories == null || categories.isEmpty()) {
                return Map.of("ok", false, "msg", "AI未能分析出有效内容");
            }


            // 清空数据库中的知识库
            wikiDao.clearAll();
            for (Map<String, Object> c : categories) {
                String cat = (String) c.get("category");
                String summary = (String) c.getOrDefault("summary", "");
                String diffDist = (String) c.getOrDefault("difficultyDist", "");
                String quality = (String) c.getOrDefault("qualityIssues", "");
                String related = (String) c.getOrDefault("relatedConcepts", "");
                wikiDao.saveOrUpdate(cat, summary, diffDist, quality, related);
            }



            // 返回知识库记录
            return Map.of("ok", true, "data", wikiDao.listAll());
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "生成失败：" + e.getMessage());
        }
    }


}

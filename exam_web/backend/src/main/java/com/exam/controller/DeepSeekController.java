package com.exam.controller;

import com.exam.service.DeepSeekService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 智能出题控制器
 * 通过调用 DeepSeek 大模型 API，根据考点关键词自动生成单选题
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/deepseek")
public class DeepSeekController {

    @Autowired
    private DeepSeekService deepseek;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * AI 出题接口
     * 请求体：{keyword: "考点关键词"}
     * 流程：
     * 1. 构建 system prompt（角色：专业出题老师）和 user prompt（具体考点）
     * 2. 调用 DeepSeek Chat API，传入 model/deepseek-chat、温度0.7
     * 3. 解析 AI 返回的 JSON，提取 questions 数组
     * 4. 清理 markdown 标记（```json ... ```）后返回干净的题目数据
     * 返回格式：
     * {ok: true, data: [{title, optionA, optionB, optionC, optionD, answer, category}]}
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        // 从请求体中提取考点关键词和难度等级
        String keyword = (String) body.get("keyword");
        Object diffObj = body.get("difficulty");
        // 校验难度等级是否为空
        int difficulty = diffObj instanceof Number ? ((Number) diffObj).intValue() : 3;
        // 校验考点关键词是否为空
        if (keyword == null || keyword.isBlank()) {
            return Map.of("ok", false, "msg", "请输入考点关键词");
        }


        
        try {
            String diffStr = difficulty == 1 ? "非常简单" : difficulty == 2 ? "比较简单"
                    : difficulty == 4 ? "比较困难" : difficulty == 5 ? "非常困难" : "中等";

            String systemPrompt = "你是一个专业的出题老师。请根据用户输入的考点，生成5道单选题。" +
                    "每道题包含：题干、A/B/C/D四个选项、正确答案、题目分类（与考点一致）、难度。" +
                    "选项要有迷惑性。严格按照JSON格式返回。";

            String userPrompt = String.format(
                    "考点：%s。题目难度：%d级（%s）。请生成5道单选题，返回格式如下：\n" +
                    "{\"questions\":[{\"title\":\"题干\",\"optionA\":\"选项A\",\"optionB\":\"选项B\",\"optionC\":\"选项C\",\"optionD\":\"选项D\",\"answer\":\"A\",\"difficulty\":%d,\"category\":\"%s\"}]}",
                    keyword, difficulty, diffStr, difficulty, keyword);
            
                    // 构建最终提示词
                    // 包含 system prompt 和 user prompt
                    // 最大 token 数 4096，温度 0.7
                    // 调用 DeepSeek Chat API，获取 AI 返回的 JSON 字符串
                    // 提取 JSON 字符串中的 questions 数组
                    // 清理 markdown 标记（```json ... ```）后返回干净的题目数据
            String prompt = systemPrompt + "\n\n" + userPrompt;
            String content = deepseek.call(prompt, 4096, 0.7);

            // 从 AI 返回的 JSON 字符串中提取 questions 数组
            content = deepseek.extractJson(content);
            
            // 解析 JSON 字符串为 Map 对象
            // 提取 questions 数组
            @SuppressWarnings("unchecked")
            // 校验 JSON 字符串是否包含 questions 数组
            Map<String, Object> parsed = mapper.readValue(content, Map.class);

            @SuppressWarnings("unchecked")
            // 校验 questions 数组是否为空
            List<Map<String, Object>> questions = (List<Map<String, Object>>) parsed.get("questions");
            /*
            parsed  =  {
            "questions"  →  [
             {"title": "Java中...", "optionA": "...", "answer": "A", ...},   ← 第1题
            {"title": "关于Spring...", "optionA": "...", "answer": "B", ...}, ← 第2题
            ...共5题
                        ]
                }
            */
                //parsed.get("questions") 取出 questions 数组中的每个 Map
                /*
                    [
                    {"title": "Java中...", ...},   ← Map
                    {"title": "关于Spring...", ...}, ← Map
                          ...
                    ]
                */
            
                    /*
                    强制类型转换
                    (List<Map<String, Object>>)
                    告诉 Java： "这个 Object 其实是一个 List，里面每个元素是 Map"
                    */
            if (questions == null || questions.isEmpty()) {
                return Map.of("ok", false, "msg", "AI返回格式异常，请重试");
            }

            return Map.of("ok", true, "data", questions);


        } catch (Exception e) {
            return Map.of("ok", false, "msg", "请求失败：" + e.getMessage());
        }
    }
}

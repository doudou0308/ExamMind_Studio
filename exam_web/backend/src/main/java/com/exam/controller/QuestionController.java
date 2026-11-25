package com.exam.controller;

import com.exam.dao.QuestionDao;
import com.exam.model.Question;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题库管理控制器
 * 负责：题目的增删改查，支持分页列表和全量导出（供考试模块使用）
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/question")
public class QuestionController {
    private final QuestionDao dao = new QuestionDao();

    /**
     * 分页查询题目列表
     * 参数：page(页码，默认1), pageSize(每页条数，默认10)
     * 返回：分页后的题目数据 + total总数
     */
    @GetMapping("/list")                                                    // 映射 GET /api/admin/question/list
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,                     // 页码，没传则默认第1页
            @RequestParam(defaultValue = "10") int pageSize,                // 每页条数，没传则默认10条
            @RequestParam(required = false) Integer id,                     // 按题目ID精确查询（可选）
            @RequestParam(required = false) String category) {              // 按分类过滤（可选）

        List<Question> all = dao.listAll();                                 // 从数据库查出全部题目

        if (id != null) {                                                   // 如果传了 id 参数
            int targetId = id;                                              // 拆箱 Integer → int
            all = all.stream()                                              // 遍历全部题目
                    .filter(q -> q.getId() == targetId)                     // 只保留 ID 匹配的那道题
                    .collect(Collectors.toList());                          // 收集为新的 List
        }

        if (category != null && !category.isBlank()) {                      // 如果传了分类且不为空
            all = all.stream()                                              // 遍历当前题目列表
                     .filter(q -> category.equals(q.getCategory()))          // 只保留分类匹配的题
                     .collect(Collectors.toList());                          // 收集为新的 List
        }

        int total = all.size();                                             // 过滤后的总题数

        if (page < 1) page = 1;                                             // 页码至少为1，防止负数

        int offset = (page - 1) * pageSize;                                 // 计算起始位置：第1页从0开始，第2页从10开始...
        int end = Math.min(offset + pageSize, total);                       // 计算结束位置，不能超过总数

        List<Question> paged = offset < total                               // 如果起始位置在有效范围内
                ? all.subList(offset, end)                                  // 截取当前页的数据
                : List.of();                                                // 否则返回空列表

        return Map.of("ok", true,                                           // 返回成功
                "data", paged,                                              // 当前页题目列表
                "total", total,                                             // 总条数（前端用于算总页数）
                "page", page,                                               // 当前页码
                "pageSize", pageSize);                                      // 每页条数
    }

    /**
     * 新增一道题目
     * 请求体：Question对象（title/选项/答案/分类/难度）
     * 默认值：题型默认choice，难度默认3（中等）
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Question q) {//反序列化请求体为Question对象
        // 检查题型是否为空，若为空则设为choice
        if (q.getType() == null || q.getType().isBlank()) 
            q.setType("choice");
        // 检查难度是否在1-5之间，若不在则设为3
               if (q.getDifficulty() < 1 || q.getDifficulty() > 5)
            q.setDifficulty(3);
        dao.add(q);
        return Map.of("ok", true, "msg", "添加成功");
    }

    /**
     * 更新一道题目（通过题目ID匹配）
     * 请求体：Question对象（含id）
     */
    @PutMapping("/update")
    public Map<String, Object> update(@RequestBody Question q) {//反序列化请求体为Question对象
        // 检查题型是否为空，若为空则设为choice
        if (q.getType() == null || q.getType().isBlank()) 
            q.setType("choice");
        // 检查难度是否在1-5之间，若不在则设为3
               if (q.getDifficulty() < 1 || q.getDifficulty() > 5)
            q.setDifficulty(3);
        dao.update(q);
        return Map.of("ok", true, "msg", "更新成功");
    }

    /**
     * 删除一道题目
     * 路径参数：id（题目ID）
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable int id) {//删除指定ID的题目
        dao.delete(id);
        return Map.of("ok", true, "msg", "已删除");
    }

    /**
     * 获取题库中所有不重复的分类名称
     * 返回：分类字符串列表，按字典升序排列
     */
    @GetMapping("/categories")
    public Map<String, Object> categories() {//获取所有不重复的分类名称
        return Map.of("ok", true, "data", dao.getAllCategories());
    }
}

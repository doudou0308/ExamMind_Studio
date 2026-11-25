package com.exam.util;

import com.exam.model.Question;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 遗传算法组卷工具类
 *
 * 核心功能：从题库（pool）中自动选取一组题目，使得：
 *   1. 题目数量 = targetSize
 *   2. 平均难度 ≈ targetAvgDifficulty
 *   3. 覆盖指定的知识点分类（targetCategories）
 *
 * 算法原理（模拟自然进化）：
 *   ① 初始化种群 —— 随机生成 POP_SIZE 个"个体"（每个个体是一套题目组合）
 *   ② 计算适应度 —— 对每个个体打分，越接近目标的分数越高
 *   ③ 精英保留 —— 适应度最高的前 20% 直接进入下一代
 *   ④ 选择 + 交叉 —— 用锦标赛法选父母，单点交叉产生子代
 *   ⑤ 变异 —— 10% 概率随机替换一道题，增加多样性
 *   ⑥ 重复 ②~⑤ 共 GENERATIONS 代，最终取适应度最高的个体作为结果
 */
public class GeneticAlgorithm {

    /** 种群规模：每代有多少个个体（题目组合） */
    private static final int POP_SIZE = 20;

    /** 进化代数：算法迭代次数 */
    private static final int GENERATIONS = 30;

    /** 精英保留比例：每代前 20% 的最优个体直接进入下一代，不被淘汰 */
    private static final double ELITE_RATE = 0.2;

    /** 变异概率：每个子代有 10% 的概率发生随机突变 */
    private static final double MUTATION_RATE = 0.1;

    /** 随机数生成器 */
    private static final Random rand = new Random();

    /**
     * 遗传算法主入口 —— 从题库中自动组卷
     *
     * @param pool                题库中的所有题目
     * @param targetSize          需要的题目数量
     * @param targetAvgDifficulty 目标平均难度（如 3.0）
     * @param targetCategories    需要覆盖的知识点分类集合
     * @return 入选的题目 ID 列表（按顺序排列）
     */
    public static List<Integer> evolve(List<Question> pool, int targetSize,
                                    double targetAvgDifficulty, Set<String> targetCategories) {
        // 题库不够大 → 直接返回全部题目，不跑算法
        if (pool.size() < targetSize) 
            return pool.stream()// 提取所有题目的ID
            .map(Question::getId)// 把每个题目转换为它的ID值
            .collect(Collectors.toList());// 

        // ① 初始化种群：随机洗牌后取前 targetSize 道题，重复生成 POP_SIZE 个个体
        List<List<Integer>> population = new ArrayList<>();
        for (int i = 0; i < POP_SIZE; i++) {                     // 生成 20 个个体
            List<Integer> individual = new ArrayList<>();         // 每个个体是一套题目的ID列表
            List<Question> shuffled = new ArrayList<>(pool);      // 拷贝题库（避免打乱原数据）
            Collections.shuffle(shuffled, rand);                  // 随机打乱题目顺序
            for (int j = 0; j < targetSize; j++) {               // 取打乱后的前 N 道题
                individual.add(shuffled.get(j).getId());         // 只存题目ID，不存完整对象
            }
            population.add(individual);                          // 这个个体加入种群
        }

        // 构建 ID → 题目映射，方便后续按 ID 快速查找题目属性
        Map<Integer, Question> idMap = pool.stream().collect(Collectors.toMap(Question::getId, q -> q));

        // ②~⑥ 迭代进化，共 30 代
        for (int gen = 0; gen < GENERATIONS; gen++) {
            // ② 计算适应度：按得分从高到低排序（越接近目标难度+分类的个体排越前）
            List<List<Integer>> scored = new ArrayList<>(population);
            // 按适应度从高到低排序，把好的个体排在前面
            Collections.sort(scored, new Comparator<List<Integer>>() {
                @Override
                public int compare(List<Integer> a, List<Integer> b) {
                    // 计算两个个体的适应度分数
                    double fitnessA = fitness(a, idMap, targetAvgDifficulty, targetCategories);
                    double fitnessB = fitness(b, idMap, targetAvgDifficulty, targetCategories);
                    // 让分数高的排在前面，所以用B减A
                    return Double.compare(fitnessB, fitnessA);
                }
            });

            // ③ 精英保留：前 20%（4个）最优个体直接复制到下一代，不被淘汰
            int eliteCount = (int) (POP_SIZE * ELITE_RATE);
            List<List<Integer>> nextGen = new ArrayList<>();
            for (int i = 0; i < eliteCount; i++) nextGen.add(new ArrayList<>(scored.get(i)));

            // ④ 选择 + ⑤ 交叉 + ⑥ 变异：填满剩余 16 个位置
            while (nextGen.size() < POP_SIZE) {
                List<Integer> p1 = tournamentSelect(scored, idMap, targetAvgDifficulty, targetCategories); // ④ 锦标赛选父亲
                List<Integer> p2 = tournamentSelect(scored, idMap, targetAvgDifficulty, targetCategories); // ④ 锦标赛选母亲
                List<Integer> child = crossover(p1, p2);                    // ⑤ 单点交叉：父母交换部分题目ID
                if (rand.nextDouble() < MUTATION_RATE) mutate(child, pool);  // ⑥ 10%概率随机替换一道题
                nextGen.add(child);                                          // 子代加入下一代
            }
            population = nextGen;  // 新一代替换旧一代
        }

        // 进化结束，返回适应度最高的个体
        List<List<Integer>> finalScored = new ArrayList<>(population);
        finalScored.sort((a, b) -> Double.compare(fitness(b, idMap, targetAvgDifficulty, targetCategories),
                                                   fitness(a, idMap, targetAvgDifficulty, targetCategories)));
        return finalScored.get(0);
    }

    /**
     * 适应度函数 —— 评价一个个体的"好"程度
     *
     * 计算公式：适应度 = 100 - 难度偏差惩罚 - 分类缺失惩罚
     *   - 难度偏差惩罚 = |实际平均难度 - 目标平均难度| × 10
     *   - 分类缺失惩罚 = (目标分类数 - 已覆盖分类数) × 5
     *
     * 分数越高 = 越接近目标需求
     *
     * @param individual       个体的题目 ID 列表
     * @param idMap            ID → 题目映射
     * @param targetAvg        目标平均难度
     * @param targetCategories 目标知识点分类
     * @return 适应度分数（越高越好）
     */
    private static double fitness(List<Integer> individual, Map<Integer, Question> idMap,
                                   double targetAvg, Set<String> targetCategories) {
        // 计算当前个体的实际平均难度
        double avgDiff = individual.stream()
                .mapToInt(id -> idMap.get(id).getDifficulty()).average().orElse(0);
        double diffPenalty = Math.abs(avgDiff - targetAvg) * 10;

        // 统计当前个体覆盖了多少个目标分类（大小写不敏感匹配）
        long catCount = individual.stream()
                .map(id -> idMap.get(id).getCategory()).filter(Objects::nonNull)
                .filter(c -> targetCategories.stream().anyMatch(t -> c.toLowerCase().contains(t.toLowerCase())))
                .distinct().count();
        double catPenalty = (targetCategories.size() - catCount) * 5;

        return 100 - diffPenalty - catPenalty;
    }

    /**
     * 锦标赛选择 —— 随机挑两个个体，适应度高的胜出
     * 这种方式既能保证选到优质个体，又保留了随机性，防止过早收敛
     *
     * @param scored            排序后的种群
     * @param idMap             ID → 题目映射
     * @param targetAvg         目标平均难度
     * @param targetCategories  目标知识点分类
     * @return 胜出的个体（深拷贝）
     */
    private static List<Integer> tournamentSelect(List<List<Integer>> scored, Map<Integer, Question> idMap,
                                                    double targetAvg, Set<String> targetCategories) {
        int a = rand.nextInt(scored.size());
        int b = rand.nextInt(scored.size());
        double fa = fitness(scored.get(a), idMap, targetAvg, targetCategories);
        double fb = fitness(scored.get(b), idMap, targetAvg, targetCategories);
        return new ArrayList<>(fa >= fb ? scored.get(a) : scored.get(b));
    }

    /**
     * 单点交叉 —— 选一个随机交叉点，前半段取父本、后半段取母本
     * 交叉后可能产生重复题目，由 dedup 方法去重
     *
     * @param p1 父本
     * @param p2 母本
     * @return 子代个体
     */
    private static List<Integer> crossover(List<Integer> p1, List<Integer> p2) {
        List<Integer> child = new ArrayList<>();
        int point = rand.nextInt(p1.size());
        for (int i = 0; i < p1.size(); i++) {
            child.add(i < point ? p1.get(i) : p2.get(i));
        }
        return dedup(child, p1, p2);
    }

    /**
     * 变异操作 —— 随机替换个体中的一道题
     * 目的是增加种群多样性，防止陷入局部最优解
     *
     * @param individual 要变异的个体
     * @param pool       题库
     */
    private static void mutate(List<Integer> individual, List<Question> pool) {
        int idx = rand.nextInt(individual.size());
        Question newQ = pool.get(rand.nextInt(pool.size()));
        individual.set(idx, newQ.getId());
    }

    /**
     * 去重补全 —— 交叉后可能有重复题目，先去掉重复的，
     * 再从父母中提取未使用的题目补到目标数量
     */
    private static List<Integer> dedup(List<Integer> child, List<Integer> p1, List<Integer> p2) {
        Set<Integer> seen = new LinkedHashSet<>();
        List<Integer> result = new ArrayList<>();
        // 先保留子代中的不重复题目
        for (int id : child) {
            if (seen.add(id)) result.add(id);
        }
        // 用父本的题目补足
        for (int id : p1) {
            if (result.size() >= p1.size()) break;
            if (seen.add(id)) result.add(id);
        }
        // 还不够的话再用母本的题目补足
        for (int id : p2) {
            if (result.size() >= p1.size()) break;
            if (seen.add(id)) result.add(id);
        }
        return result;
    }
}

# 题库与考试系统课程设计参考

本项目提供一个基于 Spring Boot + Vue 3 + MySQL 的 Java Web 课程设计实现，可用于学习题库管理系统、在线考试系统与智能组卷系统的需求分析、数据库设计和功能实现。项目包含可构建源码和启动文档，公开数据为表结构与虚构截图数据。

## 功能需求与代码对照

| 需求 | 主要代码 | 可观察行为 |
| --- | --- | --- |
| 登录与角色管理 | UserController、AuthInterceptor、AuthContext | 管理员与学生使用不同功能入口，学生首次登录需改密 |
| 题库管理 | QuestionController、QuestionDao、QuestionManage.vue | 题目增删改查、分类筛选、分页与难度字段 |
| 学生管理 | UserController、UserDao、StudentManage.vue | 管理员添加学生、删除学生、重置密码 |
| 手动组卷与考试管理 | ExamController、ExamDao、ExamManage.vue | 选题、设置时长、发布与结束考试 |
| 遗传算法自动组卷 | GeneticAlgorithm、ExamController | 按目标难度与分类评价并选择题目组合 |
| 在线考试与自动判分 | StudentExamController、GradingService、StudentExam.vue | 答题、交卷、计分、答卷回顾 |
| AI 出题 | DeepSeekController、DeepSeekService | 根据考点和难度生成题目，经确认后入库 |
| 错题分析与复习 | StudentExamController、WrongQuestionAnalysisDao | 错题查询、AI 解析缓存、已掌握标记 |
| 知识 Wiki 与学习周报 | WikiController、StudentExamController | 对题库或学生答题统计生成知识说明与学习建议 |
| 成绩分析与导出 | ExamController、ExportController | 区分度计算与单工作表 Excel 成绩导出 |

## 建议阅读顺序

1. 阅读根目录 README，配置独立开发数据库并启动前后端。
2. 阅读 init.sql 与 model，理解用户、题目、考试、答卷的关系。
3. 跟踪登录请求，理解 API、Controller、DAO 与 JDBC 调用路径。
4. 创建几道虚构题目，完成管理员组卷与学生交卷，核对分值计算。
5. 阅读 GeneticAlgorithm 的适应度函数，比较不同目标难度下的选题结果。
6. 配置自己的 AI Key，检查提示词、JSON 解析、错误提示及缓存命中。

## 可验证的扩展方向

- 比较随机抽题与遗传算法在目标平均难度、分类覆盖、耗时上的表现，使用固定题库与可复现的实验条件。
- 为鉴权、重复提交、异常答题输入、考试结束后的访问增加测试。
- 替换 MD5 与固定初始口令机制，为 Token 增加过期与撤销能力。
- 为题目内容修改、考试发布与成绩导出增加审计记录。

以上是扩展方向，不表示现有源码已实现这些增强。当前项目无需 Redis、Docker 或图数据库；知识图谱以分类统计和 AI 卡片展示。它也不包含编程题 OJ 沙箱。

完整启动步骤见 [README](../README.md)，模块关系见 [系统设计](architecture.md)，已知安全限制见 [SECURITY](../SECURITY.md)。

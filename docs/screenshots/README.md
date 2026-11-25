# Demo 截图说明

这里展示真实 Vue 页面配合虚构数据的渲染效果，包含管理员题库、考试管理和学生学习面板。账号统一为 demo，题目、考试、日期、成绩与答题记录均由预览脚本构造，不来自真实数据库，也未调用 AI 服务。

## 复现

安装 exam_web/frontend 的 npm 依赖后，在仓库根目录执行：

```sh
node scripts/preview_screenshots.mjs
```

然后访问以下本机地址：

- 题库管理：http://127.0.0.1:4317/?page=manage
- 考试管理：http://127.0.0.1:4317/?page=exams
- 学生面板：http://127.0.0.1:4317/?role=student&page=exam

预览服务仅绑定本机地址，在 Vite 开发转换过程中注入演示会话和固定 API 响应；业务源码文件不会被改写。它用于重现截图，不是完整在线 Demo，也不支持新增题目、交卷、导出或真实 AI 分析。未知 API 不会被转发至实际后端。

正常运行系统请按 [根目录 README](../../README.md) 启动前后端。

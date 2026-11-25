// Local-only, read-only screenshot preview. All API responses below are synthetic.
// Run after npm ci in exam_web/frontend. No database or AI service is contacted.
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../exam_web/frontend/', import.meta.url));
const require = createRequire(path.join(root, 'package.json'));
const viteEntry = path.join(path.dirname(require.resolve('vite/package.json')), 'dist/node/index.js');
const { createServer } = await import(pathToFileURL(viteEntry).href);
const questions = [
  ['Java 中用于定义接口的关键字是？', 'class', 'interface', 'extends', 'implements', 'B', 'Java 基础', 1],
  ['以下哪种集合结构遵循先进先出原则？', '栈', '队列', '哈希表', '二叉树', 'B', '数据结构', 2],
  ['SQL 中用于对查询结果进行排序的子句是？', 'GROUP BY', 'HAVING', 'ORDER BY', 'WHERE', 'C', '数据库', 2],
  ['Spring 中标记 REST 控制器的注解是？', '@Service', '@Repository', '@Component', '@RestController', 'D', 'Web 开发', 2],
  ['二分查找要求待查找序列满足什么条件？', '元素互不相同', '有序排列', '长度为偶数', '必须使用链表', 'B', '数据结构', 3],
  ['HTTP 状态码 404 通常表示什么？', '请求成功', '权限不足', '资源未找到', '服务器异常', 'C', 'Web 开发', 1],
].map((q, i) => ({ id: i + 1, title: q[0], optionA: q[1], optionB: q[2], optionC: q[3], optionD: q[4], answer: q[5], category: q[6], difficulty: q[7], type: 'choice' }));
const exams = [
  { id: 1, examName: 'Java 与 Web 基础 · 演示测验', questionCount: 6, difficulty: 1.8, duration: 30, status: 'ongoing', createTime: '2026-11-20 09:00:00', submitted: false },
  { id: 2, examName: '数据结构 · 模拟练习', questionCount: 10, difficulty: 2.6, duration: 40, status: 'finished', createTime: '2026-11-18 14:00:00', submitted: true, score: 8 },
  { id: 3, examName: '数据库基础 · 模拟练习', questionCount: 10, difficulty: 2.2, duration: 30, status: 'finished', createTime: '2026-11-15 10:00:00', submitted: true, score: 9 },
];
const responses = {
  '/api/question/list': { ok: true, data: questions, total: questions.length },
  '/api/question/categories': { ok: true, data: ['Java 基础', '数据结构', '数据库', 'Web 开发'] },
  '/api/admin/exam/list': { ok: true, data: exams, total: exams.length },
  '/api/student/exam/list': { ok: true, data: exams },
  '/api/student/stats': { ok: true, data: { examCount: 2, avgScore: 8.5, wrongCount: 2, accuracy: 85 } },
  '/api/student/score-history': { ok: true, data: [
    { examName: '数据库基础', score: 9 }, { examName: '数据结构', score: 8 },
  ] },
  '/api/student/mastered-questions': { ok: true, data: [] },
  '/api/student/wrong-questions': { ok: true, data: [
    { questionId: 5, title: questions[4].title, correctAnswer: 'B', studentAnswer: 'A', category: '数据结构', examName: '数据结构 · 模拟练习', submitTime: '2026-11-18 14:20:00' },
  ] },
};
const server = await createServer({
  root, configFile: false, plugins: [
    (await import(pathToFileURL(require.resolve('@vitejs/plugin-vue')).href)).default(),
    {
      name: 'synthetic-screenshot-fixtures', enforce: 'pre',
      transform(code, id) {
        if (id.endsWith('/src/store/index.ts')) {
          return code.replace('loggedIn: false', 'loggedIn: true')
            .replace('user: null as UserInfo | null', "user: {id: 1, username: 'demo', role: new URLSearchParams(location.search).get('role') || 'admin'} as UserInfo | null");
        }
        if (id.endsWith('/src/App.vue')) {
          return code.replace("const page = ref('exam')", "const page = ref(new URLSearchParams(location.search).get('page') || 'manage')");
        }
      },
      transformIndexHtml(html) {
        return html.replace('</body>', '<div style="position:fixed;bottom:12px;right:16px;z-index:9999;background:#fff;border:1px solid #cbd5e1;border-radius:8px;padding:6px 12px;color:#475569;font:12px sans-serif">演示数据 · 无真实学生信息</div></body>');
      },
      configureServer(dev) {
        dev.middlewares.use((req, res, next) => {
          const pathname = new URL(req.url, 'http://localhost').pathname;
          if (!pathname.startsWith('/api/')) return next();
          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          if (req.method !== 'GET') { res.statusCode = 405; res.end(JSON.stringify({ ok: false, msg: '截图演示只支持读取虚构数据' })); return; }
          res.end(JSON.stringify(responses[pathname] || { ok: true, data: [] }));
        });
      },
    },
  ], server: { host: '127.0.0.1', port: 4317, strictPort: true },
});
await server.listen();
console.log('Synthetic screenshot preview: http://127.0.0.1:4317/?page=manage');
console.log('Student preview: http://127.0.0.1:4317/?role=student&page=exam');

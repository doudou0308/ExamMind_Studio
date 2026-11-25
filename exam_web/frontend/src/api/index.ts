/** API 层 —— 所有与后端交互的 HTTP 请求方法集中定义在此处 */

import { store } from '../store'

const BASE = '/api/question'  // 题库相关接口前缀

/** 构造请求头：有请求体时加 Content-Type，登录后自动带 Authorization token */
function headers(hasBody = false): Record<string, string> {
  const h: Record<string, string> = {}
  if (hasBody) h['Content-Type'] = 'application/json'
  if (store.token) h['Authorization'] = store.token
  return h
}

/* ==================== 用户认证相关 ==================== */

/** 用户登录：POST /api/user/login，返回 { ok, user, msg } */
export async function login(username: string, password: string) {
  const res = await fetch('/api/user/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  return res.json()
}

/** 获取所有学生名称列表，供登录页下拉选择（无需登录态） */
export async function getStudentNames() {
  const res = await fetch('/api/user/student-names')
  return res.json()
}

/** 修改密码：POST /api/user/change-password */
export async function changePassword(oldPassword: string, newPassword: string) {
  const res = await fetch('/api/user/change-password', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ oldPassword, newPassword })
  })
  return res.json()
}

/* ==================== 题库管理（管理员） ==================== */

/** 分页获取题目列表：GET /api/question/list */
export async function listQuestions(page = 1, pageSize = 10, id?: number, category?: string) {
  let url = `${BASE}/list?page=${page}&pageSize=${pageSize}`
  if (id) url += `&id=${id}`
  if (category) url += `&category=${encodeURIComponent(category)}`
  const res = await fetch(url, { headers: headers() })
  return res.json()
}

/** 新增题目：POST /api/question/add */
export async function addQuestion(q: any) {
  await fetch(`${BASE}/add`, {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify(q)
  })
}

/** 更新题目：PUT /api/question/update */
export async function updateQuestion(q: any) {
  await fetch(`${BASE}/update`, {
    method: 'PUT',
    headers: headers(true),
    body: JSON.stringify(q)
  })
}

/** 删除题目：DELETE /api/question/delete/:id */
export async function deleteQuestion(id: number) {
  await fetch(`${BASE}/delete/${id}`, { method: 'DELETE', headers: headers() })
}

/* ==================== 学生管理（管理员） ==================== */

/** 添加学生：POST /api/user/add，返回 { ok, msg } */
export async function addStudent(username: string, password: string) {
  const res = await fetch('/api/user/add', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ username, password })
  })
  return res.json()
}

/** 分页获取学生列表：GET /api/user/students */
export async function listStudents(page = 1, pageSize = 10) {
  const res = await fetch(`/api/user/students?page=${page}&pageSize=${pageSize}`, { headers: headers() })
  return res.json()
}

/** 删除学生：DELETE /api/user/delete/:id */
export async function deleteStudent(id: number) {
  const res = await fetch(`/api/user/delete/${id}`, { method: 'DELETE', headers: headers() })
  return res.json()
}

/** 重置学生密码为 123456：PUT /api/user/reset-password/:id */
export async function resetStudentPassword(id: number) {
  const res = await fetch(`/api/user/reset-password/${id}`, {
    method: 'PUT',
    headers: headers()
  })
  return res.json()
}

/* ==================== 考试管理（管理员） ==================== */

/** 管理员 - 创建/下发考试（手动选题目） */
export async function createExam(examName: string, questionIds: number[], duration?: number) {
  const res = await fetch('/api/admin/exam/create', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ examName, questionIds, duration: duration || 0 })
  })
  return res.json()
}

/** 管理员 - 自动组卷（系统从题库随机抽题生成试卷） */
export async function autoGenerateExam(examName: string, questionCount: number, category?: string, difficulty?: number, duration?: number, useGA?: boolean) {
  const res = await fetch('/api/admin/exam/auto-generate', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ examName, questionCount, category: category || '', difficulty: difficulty || null, duration: duration || 0, useGA: useGA || false })
  })
  return res.json()
}

/** 管理员 - 获取所有考试列表 */
export async function listExams(page = 1, pageSize = 10) {
  const res = await fetch(`/api/admin/exam/list?page=${page}&pageSize=${pageSize}`, { headers: headers() })
  return res.json()
}

/** 管理员 - 删除考试 */
export async function deleteExam(id: number) {
  const res = await fetch(`/api/admin/exam/delete/${id}`, { method: 'DELETE', headers: headers() })
  return res.json()
}

/** 管理员 - 结束考试 */
export async function finishExam(id: number) {
  const res = await fetch(`/api/admin/exam/finish/${id}`, { method: 'PUT', headers: headers() })
  return res.json()
}

/** 管理员 - 查看某场考试的所有学生答卷（分页） */
export async function listExamRecords(examId: number, page = 1, pageSize = 10) {
  const res = await fetch(`/api/admin/exam/records/${examId}?page=${page}&pageSize=${pageSize}`, { headers: headers() })
  return res.json()
}

/** 管理员 - 查看某份答卷的详细内容（每题得分情况） */
export async function getRecordDetail(recordId: number) {
  const res = await fetch(`/api/admin/exam/detail/${recordId}`, { headers: headers() })
  return res.json()
}

/** 管理员 - 导出考试成绩为 Excel 文件的下载链接 */
export function exportExamUrl(examId: number) {
  return `/api/admin/exam/export/${examId}?token=${encodeURIComponent(store.token)}`
}

/** 管理员 - 题目区分度分析（衡量每道题区分高水平/低水平学生的能力） */
export async function getDiscrimination(examId: number) {
  const res = await fetch(`/api/admin/exam/discrimination/${examId}`, { headers: headers() })
  return res.json()
}

/** 管理员 - AI 智能出题（调用 DeepSeek 根据关键词和难度生成题目） */
export async function deepseekGenerate(keyword: string, difficulty?: number) {
  const res = await fetch('/api/admin/deepseek/generate', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ keyword, difficulty: difficulty || 3 })
  })
  return res.json()
}

/* ==================== 学生端功能 ==================== */

/** 学生 - 获取可参加的考试列表 */
export async function getStudentExams() {
  const res = await fetch('/api/student/exam/list', { headers: headers() })
  return res.json()
}

/** 学生 - 加载某场考试的完整题目内容 */
export async function getExamQuestions(examId: number) {
  const res = await fetch(`/api/student/exam/questions/${examId}`, { headers: headers() })
  return res.json()
}

/** 学生 - 提交考试答案（交卷），返回得分 */
export async function submitExam(examId: number, answers: Record<string, string>) {
  const res = await fetch('/api/student/exam/submit', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ examId, answers })
  })
  return res.json()
}

/** 学生 - 学习统计：考试次数、平均分、错题数、正确率 */
export async function getStudentStats() {
  const res = await fetch('/api/student/stats', { headers: headers() })
  return res.json()
}

/** 学生 - 错题本：按考试筛选或查看全部错题 */
export async function getWrongQuestions(examId?: number) {
  const url = examId ? `/api/student/wrong-questions?examId=${examId}` : '/api/student/wrong-questions'
  const res = await fetch(url, { headers: headers() })
  return res.json()
}

/** 学生 - 历次考试得分历史（用于绘制成绩趋势图） */
export async function getScoreHistory() {
  const res = await fetch('/api/student/score-history', { headers: headers() })
  return res.json()
}

/** 学生 - 查看已交卷的答卷详情（含每题正误判断） */
export async function getStudentRecordDetail(examId: number) {
  const res = await fetch(`/api/student/exam/record-detail/${examId}`, { headers: headers() })
  return res.json()
}

/** 学生 - 练习模式：按题型/分类/模式获取练习题（顺序或随机） */
export async function getPracticeQuestions(type?: string, category?: string, mode?: string) {
  const params = new URLSearchParams()
  if (type) params.set('type', type)
  if (category) params.set('category', category)
  if (mode) params.set('mode', mode)
  const res = await fetch(`/api/student/practice?${params}`, { headers: headers() })
  return res.json()
}

/** 学生 - 获取题库所有分类名称列表 */
export async function getCategories() {
  const res = await fetch('/api/question/categories', { headers: headers() })
  return res.json()
}

/** 学生 - AI 错题解析：传入错题ID列表，返回每道题的AI解析 */
export async function analyzeWrongQuestions(questionIds: number[]) {
  const res = await fetch('/api/student/wrong-question-analysis', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify({ questionIds })
  })
  return res.json()
}

/** 学生 - 标记题目为已掌握 */
export async function masterQuestion(questionId: number) {
  const res = await fetch(`/api/student/master-question/${questionId}`, {
    method: 'POST',
    headers: headers()
  })
  return res.json()
}

/** 学生 - 取消已掌握标记 */
export async function unmasterQuestion(questionId: number) {
  const res = await fetch(`/api/student/master-question/${questionId}`, {
    method: 'DELETE',
    headers: headers()
  })
  return res.json()
}

/** 学生 - 获取已掌握题目 ID 列表 */
export async function getMasteredQuestions(): Promise<number[]> {
  const res = await fetch('/api/student/mastered-questions', { headers: headers() })
  const json = await res.json()
  return json.ok ? json.data : []
}

/** 学生 - 获取知识图谱 */
export async function getKnowledgeMap() {
  const res = await fetch('/api/student/knowledge-map', { headers: headers() })
  return res.json()
}

/** 学生 - 重建知识图谱（LLM分析） */
export async function rebuildKnowledgeMap() {
  const res = await fetch('/api/student/rebuild-knowledge-map', {
    method: 'POST',
    headers: headers()
  })
  return res.json()
}

/** 学生 - 生成学习周报 */
export async function getWeeklyReport() {
  const res = await fetch('/api/student/weekly-report', { headers: headers() })
  return res.json()
}

/* ==================== 管理员 - 题库Wiki ==================== */

/** 管理员 - 获取题库知识 Wiki 列表 */
export async function getWikiList() {
  const res = await fetch('/api/admin/wiki/list', { headers: headers() })
  return res.json()
}

/** 管理员 - 重建题库知识 Wiki（LLM分析全部题目和考试记录） */
export async function rebuildWiki() {
  const res = await fetch('/api/admin/wiki/rebuild', {
    method: 'POST',
    headers: headers()
  })
  return res.json()
}



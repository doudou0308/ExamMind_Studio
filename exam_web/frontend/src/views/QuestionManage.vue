<!--
  QuestionManage.vue —— 题库管理页面（管理员专用）
  核心功能：
    1. 题目列表（分页表格，支持按标题查看详情）
    2. 添加/编辑/删除题目（弹窗表单，含难度设置）
    3. AI 智能出题（DeepSeek 根据关键词生成题目并批量导入）
    4. 题目详情查看（点击标题展开）
-->
<template>
  <div>
    <!-- 顶部操作栏：添加题目 + AI出题 + 总数提示 -->
    <div class="bar">
      <button class="btn-primary" @click="showAdd = true">+ 添加题目</button>
      <button class="btn-ai" @click="showAi = true">AI出题</button>
      <span class="tip">共 {{ total }} 题</span>
    </div>

    <!-- 筛选栏：ID搜索 + 分类下拉 -->
    <div class="filter-bar">
      <input v-model="filterId" type="number" placeholder="输入题目ID搜索" class="filter-input filter-id"
             @keyup.enter="load" />
      <select v-model="filterCategory" class="filter-input filter-cat" @change="load">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
      </select>
      <button class="btn-clear" @click="clearFilter">清除筛选</button>
    </div>

    <!-- 题目列表表格 -->
    <div v-if="list.length" class="table-wrap">
    <table>
      <thead>
        <tr><th>ID</th><th>题目</th><th>选项</th><th>答案</th><th>分类</th><th>难度</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="(q, idx) in list" :key="q.id">
          <td class="col-id">{{ q.id }}</td>
          <td class="title-col" :title="q.title" @click="showDetail(q)">
            <span class="clamp-text">{{ q.title }}</span>
          </td>
          <td class="opt-col" :title="optTooltip(q)">
            <span class="opt-grid">
              <span>A.{{ q.optionA }}</span><span>B.{{ q.optionB }}</span>
              <span>C.{{ q.optionC }}</span><span>D.{{ q.optionD }}</span>
            </span>
          </td>
          <td class="col-ans">{{ q.answer }}</td>
          <td class="col-cat">{{ q.category }}</td>
          <td class="col-diff">{{ q.difficulty || 3 }}</td>
          <td class="col-act">
            <button class="btn-edit" @click="openEdit(q)">编辑</button>
            <button class="btn-del" @click="confirmDel(q)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    <p v-else class="empty">暂无题目</p>

    <!-- 分页导航 -->
    <div v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页 (共 {{ total }} 题)</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>

    <!-- ==================== 添加题目弹窗 ==================== -->
    <div v-if="showAdd" class="modal" @click.self="closeAdd">
      <div class="modal-box">
        <h3>添加题目</h3>
        <label>题目 <input v-model="form.title" /></label>
        <label>选项A <input v-model="form.optionA" /></label>
        <label>选项B <input v-model="form.optionB" /></label>
        <label>选项C <input v-model="form.optionC" /></label>
        <label>选项D <input v-model="form.optionD" /></label>
        <label>正确答案 <input v-model="form.answer" placeholder="如 A" /></label>
        <label>分类 <input v-model="form.category" /></label>
        <label>难度
          <select v-model="form.difficulty" style="width:100%;padding:8px;border:1px solid #ddd;border-radius:6px;margin-top:4px">
            <option :value="1">1 - 非常简单</option>
            <option :value="2">2 - 简单</option>
            <option :value="3">3 - 中等</option>
            <option :value="4">4 - 困难</option>
            <option :value="5">5 - 非常困难</option>
          </select>
        </label>
        <div class="modal-btn">
          <button @click="closeAdd">取消</button>
          <button class="btn-primary" @click="add">确定</button>
        </div>
      </div>
    </div>

    <!-- ==================== 编辑题目弹窗 ==================== -->
    <div v-if="showEdit" class="modal" @click.self="closeEdit">
      <div class="modal-box">
        <h3>编辑题目</h3>
        <label>题目 <input v-model="form.title" /></label>
        <label>选项A <input v-model="form.optionA" /></label>
        <label>选项B <input v-model="form.optionB" /></label>
        <label>选项C <input v-model="form.optionC" /></label>
        <label>选项D <input v-model="form.optionD" /></label>
        <label>正确答案 <input v-model="form.answer" placeholder="如 A" /></label>
        <label>分类 <input v-model="form.category" /></label>
        <label>难度
          <select v-model="form.difficulty" style="width:100%;padding:8px;border:1px solid #ddd;border-radius:6px;margin-top:4px">
            <option :value="1">1 - 非常简单</option>
            <option :value="2">2 - 简单</option>
            <option :value="3">3 - 中等</option>
            <option :value="4">4 - 困难</option>
            <option :value="5">5 - 非常困难</option>
          </select>
        </label>
        <div class="modal-btn">
          <button @click="closeEdit">取消</button>
          <button class="btn-primary" @click="update">保存</button>
        </div>
      </div>
    </div>

    <!-- ==================== AI 出题弹窗 ==================== -->
    <div v-if="showAi" class="modal" @click.self="closeAi">
      <div class="modal-box" style="width:640px;max-height:85vh">
        <h3>AI 智能出题</h3>
        <div class="search-row">
          <input v-model="aiKeyword" placeholder="输入考点关键词，如：Java多线程、数据结构、网络安全..." @keyup.enter="doAiGenerate" />
          <select v-model.number="aiDifficulty" style="width:150px;padding:8px;border:1px solid #ddd;border-radius:6px;font-size:14px">
            <option :value="1">1 - 非常简单</option>
            <option :value="2">2 - 简单</option>
            <option :value="3">3 - 中等</option>
            <option :value="4">4 - 困难</option>
            <option :value="5">5 - 非常困难</option>
          </select>
          <button class="btn-primary" :disabled="aiLoading" @click="doAiGenerate">
            {{ aiLoading ? '生成中...' : '生成5道题' }}
          </button>
        </div>

        <p v-if="aiError" class="ext-error">{{ aiError }}</p>

        <!-- AI 生成的题目列表，勾选后批量导入 -->
        <div v-if="aiQuestions.length" class="ai-results">
          <div class="ai-header">
            <span>已生成 {{ aiQuestions.length }} 道题目，勾选后导入</span>
            <label class="select-all"><input type="checkbox" v-model="selectAll" @change="toggleAll" /> 全选</label>
          </div>
          <div v-for="(q, i) in aiQuestions" :key="i" class="ai-card" :class="{ selected: aiSelected[i] }">
            <label class="ai-check">
              <input type="checkbox" v-model="aiSelected[i]" />
              <span class="ai-num">{{ i + 1 }}</span>
            </label>
            <div class="ai-content">
              <p class="ai-title">{{ q.title }}</p>
              <p class="ai-opts">A. {{ q.optionA }} &nbsp; B. {{ q.optionB }} &nbsp; C. {{ q.optionC }} &nbsp; D. {{ q.optionD }}</p>
              <p class="ai-ans">答案：<strong>{{ q.answer }}</strong> &nbsp; 分类：{{ q.category }}</p>
            </div>
          </div>
          <div class="modal-btn">
            <button @click="closeAi">取消</button>
            <button class="btn-primary" :disabled="!hasSelection || importLoading" @click="doImportSelected">
              {{ importLoading ? '导入中...' : `导入选中 (${selectedCount})` }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 题目详情弹窗 ==================== -->
    <div v-if="showDetailModal" class="modal" @click.self="showDetailModal = false">
      <div class="modal-box" style="width:560px">
        <h3>题目详情</h3>
        <div class="detail-row">
            <span class="detail-label">难度：</span><span>{{ detailTarget?.difficulty || 3 }}</span>
          </div>
        <div class="detail-block">
          <p class="detail-label">题干</p>
          <p class="detail-text">{{ detailTarget?.title }}</p>
        </div>
        <div class="detail-block">
          <p class="detail-label">选项</p>
          <p class="detail-text">A. {{ detailTarget?.optionA }}</p>
          <p class="detail-text">B. {{ detailTarget?.optionB }}</p>
          <p class="detail-text">C. {{ detailTarget?.optionC }}</p>
          <p class="detail-text">D. {{ detailTarget?.optionD }}</p>
        </div>
        <div class="detail-row">
          <span class="detail-label">答案：</span><strong>{{ detailTarget?.answer }}</strong>
          <span style="margin-left:24px" class="detail-label">分类：</span><span>{{ detailTarget?.category }}</span>
        </div>
        <div class="modal-btn">
          <button @click="showDetailModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- ==================== 删除确认弹窗 ==================== -->
    <div v-if="showDel" class="modal" @click.self="showDel = false">
      <div class="modal-box">
        <h3>确认删除</h3>
        <p class="del-msg">确定要删除题目 <strong>"{{ delTarget?.title }}"</strong> 吗？此操作不可撤销。</p>
        <div class="modal-btn">
          <button @click="showDel = false">取消</button>
          <button class="btn-danger" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listQuestions, addQuestion, updateQuestion, deleteQuestion, deepseekGenerate, getCategories } from '../api'

/** 题目数据结构 */
interface Q { id: number; title: string; optionA: string; optionB: string; optionC: string; optionD: string; answer: string; category: string; type: string; difficulty: number }

/** 题目列表 */
const list = ref<Q[]>([])
/** 分页相关 */
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)
/** 筛选状态 */
const filterId = ref<number | undefined>(undefined)
const filterCategory = ref('')
const categories = ref<string[]>([])

/** 各弹窗显示状态 */
const showAdd = ref(false)          // 添加弹窗
const showEdit = ref(false)         // 编辑弹窗
const showDel = ref(false)          // 删除确认弹窗
const showDetailModal = ref(false)  // 题目详情弹窗
/** 详情/删除的目标题目 */
const detailTarget = ref<Q | null>(null)
const delTarget = ref<Q | null>(null)
/** 添加/编辑表单数据 */
const form = ref({ id: 0, title: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', category: '', difficulty: 3 })

/** AI 出题相关 */
const showAi = ref(false)              // AI出题弹窗
const aiKeyword = ref('')              // 考点关键词
const aiDifficulty = ref(3)            // AI出题难度
const aiQuestions = ref<any[]>([])     // AI生成的题目列表
const aiSelected = ref<boolean[]>([])  // 每道题的勾选状态
const aiLoading = ref(false)           // 生成中加载状态
const aiError = ref('')                // 生成错误提示
const importLoading = ref(false)       // 导入中加载状态
const selectAll = ref(false)           // 全选复选框

/** 是否有选中的题目 */
const hasSelection = computed(() => aiSelected.value.some(Boolean))
/** 选中题目数量 */
const selectedCount = computed(() => aiSelected.value.filter(Boolean).length)

/** 加载题目列表 */
async function load() {
  const r = await listQuestions(page.value, pageSize, filterId.value, filterCategory.value)
  if (r.ok) { list.value = r.data; total.value = r.total }
}

/** 加载分类列表 */
async function loadCategories() {
  const r = await getCategories()
  if (r.ok) categories.value = r.data || []
}

/** 清除筛选条件 */
function clearFilter() {
  filterId.value = undefined
  filterCategory.value = ''
  page.value = 1
  load()
}

onMounted(() => { load(); loadCategories() })

/** 分页跳转 */
function goPage(p: number) { page.value = p; load() }

/** 添加题目 → 调接口 → 关闭弹窗 → 刷新列表 */
async function add() {
  await addQuestion(form.value)
  closeAdd()
  load()
}

/** 关闭添加弹窗并重置表单 */
function closeAdd() {
  showAdd.value = false
  form.value = { id: 0, title: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', category: '', difficulty: 3 }
}

/** 打开编辑弹窗，回填题目数据 */
function openEdit(q: Q) {
  form.value = { id: q.id, title: q.title, optionA: q.optionA, optionB: q.optionB, optionC: q.optionC, optionD: q.optionD, answer: q.answer, category: q.category, difficulty: q.difficulty || 3 }
  showEdit.value = true
}

/** 保存编辑 */
async function update() {
  await updateQuestion(form.value)
  closeEdit()
  load()
}

/** 关闭编辑弹窗并重置表单 */
function closeEdit() {
  showEdit.value = false
  form.value = { id: 0, title: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', category: '', difficulty: 3 }
}

/** AI 生成题目：调用 DeepSeek 接口，返回后默认全选 */
async function doAiGenerate() {
  if (!aiKeyword.value) return
  aiError.value = ''
  aiQuestions.value = []
  aiLoading.value = true
  try {
    const r = await deepseekGenerate(aiKeyword.value, aiDifficulty.value)
    if (r.ok) {
      aiQuestions.value = r.data
      aiSelected.value = new Array(r.data.length).fill(true)  // 默认全选
      selectAll.value = true
    } else {
      aiError.value = r.msg || '生成失败'
    }
  } catch {
    aiError.value = '请求失败，请检查网络'
  } finally {
    aiLoading.value = false
  }
}

/** 全选/取消全选切换 */
function toggleAll() {
  aiSelected.value = new Array(aiQuestions.value.length).fill(selectAll.value)
}

/** 将勾选的 AI 题目逐条导入题库 */
async function doImportSelected() {
  importLoading.value = true
  try {
    for (let i = 0; i < aiQuestions.value.length; i++) {
      if (aiSelected.value[i]) {
        await addQuestion(aiQuestions.value[i])
      }
    }
    closeAi()
    load()
  } finally {
    importLoading.value = false
  }
}

/** 关闭 AI 出题弹窗并重置 */
function closeAi() {
  showAi.value = false
  aiKeyword.value = ''
  aiDifficulty.value = 3
  aiQuestions.value = []
  aiSelected.value = []
  aiError.value = ''
  selectAll.value = false
}

/** 打开删除确认弹窗 */
function confirmDel(q: Q) {
  delTarget.value = q
  showDel.value = true
}

/** 打开题目详情弹窗 */
function showDetail(q: Q) {
  detailTarget.value = q
  showDetailModal.value = true
}

/** 根据题目类型生成选项提示文本（用于表格 tooltip） */
function optTooltip(q: Q) {
  return `A. ${q.optionA}\nB. ${q.optionB}\nC. ${q.optionC}\nD. ${q.optionD}`
}

/** 执行删除题目 */
async function doDelete() {
  if (!delTarget.value) return
  await deleteQuestion(delTarget.value.id)
  showDel.value = false
  delTarget.value = null
  load()
}
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; width: 92%; margin-left: auto; margin-right: auto; max-width: 1600px }
.tip { color: #888; font-size: 14px }
.filter-bar { display: flex; align-items: center; gap: 10px; width: 92%; margin: 0 auto 12px; max-width: 1600px }
.filter-input { padding: 6px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 13px; outline: none }
.filter-input:focus { border-color: #4f46e5 }
.filter-id { width: 160px }
.filter-cat { width: 150px; background: #fff; cursor: pointer }
.btn-clear { padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; font-size: 12px; color: #666; white-space: nowrap }
.btn-clear:hover { background: #f3f4f6 }
.btn-primary { background: #4f46e5; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.btn-primary:disabled { opacity: .5; cursor: not-allowed }
.btn-ai { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.btn-edit { background: none; color: #4f46e5; border: 1px solid #4f46e5; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 12px; white-space: nowrap; margin-right: 8px }
.btn-del { background: none; color: #e53e3e; border: 1px solid #e53e3e; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 12px; white-space: nowrap }
.btn-danger { background: #e53e3e; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.table-wrap { width: 92%; margin: 0 auto; max-width: 1600px }
table { width: 100%; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0, 0, 0, .1) }
th, td { padding: 6px 10px; text-align: left; font-size: 14px; border-bottom: 1px solid #f0f0f0 }
th { background: #fafafa; font-weight: 600; font-size: 13px }
.col-id { width: 5%; text-align: center; font-family: monospace; color: #4f46e5 }
.col-ans { width: 4%; text-align: center; font-weight: 600 }
.col-cat { width: 8%; text-align: center; font-size: 13px; color: #666; white-space: nowrap }
.col-diff { width: 5%; text-align: center; font-weight: 600; color: #d69e2e }
.col-act { width: 14%; text-align: center; white-space: nowrap }
.title-col { width: 34%; cursor: pointer }
.title-col:hover { color: #4f46e5 }
.opt-col { width: 30%; font-size: 12px; color: #666; vertical-align: top }
.opt-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 8px;
  line-height: 1.5;
  overflow: hidden;
}
.opt-grid span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clamp-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.5;
}
.empty { text-align: center; color: #999; padding: 40px }
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px; font-size: 14px; color: #555 }
.pager button { padding: 6px 16px; border: 1px solid #ddd; border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px }
.pager button:disabled { opacity: .4; cursor: not-allowed }
.modal { position: fixed; inset: 0; background: rgba(0, 0, 0, .4); display: flex; align-items: center; justify-content: center; z-index: 100 }
.modal-box { background: #fff; border-radius: 12px; padding: 24px; width: 480px; max-height: 80vh; overflow-y: auto }
.modal-box h3 { margin-bottom: 16px }
.modal-box label { display: block; margin-bottom: 10px; font-size: 14px }
.modal-box input, .modal-box select { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 6px; margin-top: 4px; font-size: 14px }
.modal-btn { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; flex-wrap: wrap }
.modal-btn button { padding: 8px 20px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer; font-size: 14px }
.modal-btn .btn-primary { background: #4f46e5; color: #fff; border: none }
.modal-btn .btn-danger { background: #e53e3e; color: #fff; border: none }
.del-msg { font-size: 14px; color: #555; margin-bottom: 8px }
.detail-block { margin-bottom: 14px }
.detail-row { margin-bottom: 12px; font-size: 14px }
.detail-label { font-size: 12px; color: #888; margin-bottom: 4px }
.detail-text { font-size: 14px; color: #333; line-height: 1.6 }
.search-row { display: flex; gap: 10px; margin-bottom: 14px }
.search-row input { flex: 1; padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px }
.ext-error { color: #e53e3e; font-size: 13px; margin-bottom: 8px }
.ai-results { margin-top: 12px }
.ai-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 13px; color: #666 }
.select-all { display: flex; align-items: center; gap: 4px; cursor: pointer; font-size: 13px }
.select-all input { width: auto }
.ai-card { display: flex; gap: 10px; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 8px; cursor: pointer; transition: .15s }
.ai-card:hover { border-color: #4f46e5 }
.ai-card.selected { border-color: #4f46e5; background: #f5f3ff }
.ai-check { display: flex; align-items: flex-start; gap: 6px; padding-top: 2px; cursor: pointer }
.ai-check input[type="checkbox"] { width: auto; margin-top: 3px }
.ai-check .ai-num { color: #4f46e5; font-weight: 600; font-size: 14px; min-width: 20px }
.ai-content { flex: 1; min-width: 0 }
.ai-title { font-size: 14px; font-weight: 600; margin-bottom: 4px }
.ai-opts { font-size: 12px; color: #666; margin-bottom: 4px }
.ai-ans { font-size: 12px; color: #38a169 }
</style>
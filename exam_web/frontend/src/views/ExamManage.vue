<!--
  ExamManage.vue —— 考试管理页面（管理员专用）
  核心功能：
    1. 下发考试：手动选题目 or 自动组卷（支持遗传算法优化）
    2. 查看某场考试的学生成绩与答卷详情
    3. 题目区分度分析
    4. 导出考试成绩 Excel
    5. 删除考试
-->
<template>
  <div>
    <!-- 顶部操作栏 -->
    <div class="bar">
      <button class="btn-primary" @click="showCreate = true">+ 下发考试</button>
      <span class="tip">共 {{ exams.length }} 场考试</span>
    </div>

    <!-- 考试列表表格 -->
    <table v-if="exams.length">
      <thead>
        <tr><th>#</th><th>考试名称</th><th>题目数</th><th>平均难度</th><th>状态</th><th>下发时间</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="(e, idx) in exams" :key="e.id">
          <td>{{ idx + 1 }}</td>
          <td>{{ e.examName }}</td>
          <td>{{ e.questionCount }} 题</td>
          <td>{{ e.difficulty > 0 ? e.difficulty.toFixed(1) : '-' }}</td>
          <td style="white-space: nowrap">
            <span v-if="e.status === 'finished'" class="tag-finished">已结束</span>
            <span v-else class="tag-ongoing">进行中</span>
          </td>
          <td>{{ e.createTime }}</td>
          <td class="col-act">
            <button class="btn-action" @click="viewResults(e)">查看成绩</button>
            <button class="btn-action" @click="viewDiscrimination(e)">区分度</button>
            <a :href="exportExamUrl(e.id)" class="btn-export">导出Excel</a>
            <button v-if="e.status !== 'finished'" class="btn-finish" @click="doFinish(e)">结束考试</button>
            <button class="btn-del" @click="confirmDelete(e)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无考试</p>

    <!-- ==================== 下发考试弹窗 ==================== -->
    <div v-if="showCreate" class="modal" @click.self="closeCreate">
      <div class="modal-box" style="width:600px">
        <h3>下发考试</h3>

        <!-- 组卷模式切换标签 -->
        <div class="mode-tabs">
          <button :class="{ active: createMode === 'manual' }" @click="createMode = 'manual'">手动组卷</button>
          <button :class="{ active: createMode === 'auto' }" @click="createMode = 'auto'">自动组卷</button>
        </div>

        <!-- 考试名称（两种模式共用） -->
        <label>考试名称 <input v-model="examName" placeholder="如：Java基础单元测" /></label>

        <!-- 考试限时（两种模式共用） -->
        <label>考试限时（分钟，0=不限时）
          <input v-model.number="duration" type="number" min="0" placeholder="0 表示不限时" />
        </label>

        <!-- 手动组卷模式：显示题目列表 + 分类筛选 -->
        <template v-if="createMode === 'manual'">
          <div class="filter-bar">
            <label style="margin:0;display:flex;align-items:center;gap:6px">分类：
              <select v-model="filterCat" style="width:auto;margin:0">
                <option value="">全部分类</option>
                <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </label>
            <span class="tip">已选 {{ selectedIds.length }} / {{ questions.length }} 题</span>
          </div>
          <div class="q-list">
            <label v-for="q in filteredQuestions" :key="q.id" class="q-item"
              :class="{ selected: selectedIds.includes(q.id) }">
              <input type="checkbox" :value="q.id" v-model="selectedIds" />
              <span class="q-title-text">{{ q.title }}</span>
              <span class="q-cat">{{ q.category }}</span>
              <span class="q-diff">难度{{ q.difficulty || 3 }}</span>
            </label>
          </div>
        </template>

        <!-- 自动组卷模式：题目数量 + 分类筛选 + 遗传算法开关 -->
        <template v-else>
          <label>题目数量 <input v-model.number="autoCount" type="number" min="1" placeholder="需要几道题" /></label>
          <label style="display:flex;align-items:center;gap:8px">
            分类：<select v-model="autoCategory" style="width:auto;flex:1;margin:0">
              <option value="">全部</option>
              <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </label>
          <label style="display:flex;align-items:center;gap:8px">
            难度：<select v-model.number="autoDifficulty" style="width:auto;flex:1;margin:0">
              <option :value="0">全部难度</option>
              <option :value="1">1 - 非常简单</option>
              <option :value="2">2 - 简单</option>
              <option :value="3">3 - 中等</option>
              <option :value="4">4 - 困难</option>
              <option :value="5">5 - 非常困难</option>
            </select>
          </label>
          <label style="display:flex;align-items:center;gap:8px">
            <input type="checkbox" v-model="useGA" style="width:auto;margin:0" />
            使用遗传算法优化组卷（题库充足时推荐开启）
          </label>
          <p class="tip">系统将从题库中随机抽取指定数量的题目自动生成试卷</p>
        </template>

        <div class="modal-btn">
          <button @click="closeCreate">取消</button>
          <button class="btn-primary" @click="createMode === 'manual' ? create() : autoCreate()">
            {{ createMode === 'manual' ? '下发' : '随机生成并下发' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 成绩查看弹窗 ==================== -->
    <div v-if="resultExam" class="modal" @click.self="resultExam = null">
      <div class="modal-box" style="width:700px">
        <h3>{{ resultExam.examName }} - 成绩列表</h3>
        <table v-if="results.length">
          <thead>
            <tr><th>#</th><th>学生</th><th>得分</th><th>交卷时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, idx) in results" :key="r.id">
              <td>{{ idx + 1 }}</td>
              <td>{{ r.studentName }}</td>
              <td>{{ r.score }}</td>
              <td>{{ r.submitTime }}</td>
              <td><button class="btn-action" @click="viewDetail(r.id)">查看答卷</button></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty">暂无学生交卷</p>
        <!-- 成绩分页 -->
        <div v-if="resultTotalPages > 1" class="pager">
          <button :disabled="resultPage <= 1" @click="goResultPage(resultPage - 1)">上一页</button>
          <span>第 {{ resultPage }} / {{ resultTotalPages }} 页</span>
          <button :disabled="resultPage >= resultTotalPages" @click="goResultPage(resultPage + 1)">下一页</button>
        </div>
        <div class="modal-btn"><button @click="resultExam = null">关闭</button></div>
      </div>
    </div>

    <!-- ==================== 答卷详情弹窗 ==================== -->
    <div v-if="detailData" class="modal" @click.self="detailData = undefined">
      <div class="modal-box" style="width:650px">
        <h3>{{ detailData.studentName }} 的答卷</h3>
        <p class="score-line">总分: {{ detailData.score }} | 交卷: {{ detailData.submitTime }}</p>
        <!-- 逐题展示：正确/错误 + 学生答案 + 正确答案 -->
        <div v-for="(d, idx) in detailData.details" :key="idx" class="d-card" :class="{ wrong: !d.correct }">
          <div class="d-title">{{ idx + 1 }}. {{ d.title }}</div>
          <div class="d-answer">
            学生答案: <strong>{{ d.studentAnswer || '未作答' }}</strong>
            <span v-if="!d.correct" class="correct-show">正确答案: {{ d.correctAnswer }}</span>
          </div>
          <div class="d-score">{{ d.correct ? '对' : '错' }}</div>
        </div>
        <div class="modal-btn"><button @click="detailData = undefined">关闭</button></div>
      </div>
    </div>

    <!-- ==================== 区分度分析弹窗 ==================== -->
    <div v-if="showDisc" class="modal" @click.self="showDisc=null">
      <div class="modal-box" style="width:650px">
        <h3>区分度分析</h3>
        <table v-if="discList.length">
          <thead><tr><th>题目</th><th>区分度</th><th>质量</th></tr></thead>
          <tbody>
            <tr v-for="d in discList" :key="d.questionId">
              <td class="clamp-text">{{ d.title }}</td>
              <td>{{ d.discrimination }}</td>
              <td :class="'quality-'+qualityClass(d.quality)">{{ d.quality }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty">暂无数据</p>
        <div class="modal-btn"><button @click="showDisc=null">关闭</button></div>
      </div>
    </div>

    <!-- ==================== 删除确认弹窗 ==================== -->
    <div v-if="delTarget" class="modal" @click.self="delTarget = undefined">
      <div class="modal-box">
        <h3>确认删除</h3>
        <p class="del-msg">确定要删除考试 <strong>"{{ delTarget.examName }}"</strong> 吗？</p>
        <div class="modal-btn">
          <button @click="delTarget = undefined">取消</button>
          <button class="btn-danger" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listExams, createExam, autoGenerateExam, deleteExam, finishExam, listQuestions, listExamRecords, getRecordDetail, exportExamUrl, getDiscrimination, getCategories } from '../api'

interface ExamItem { id: number; examName: string; questionCount: number; difficulty: number; createTime: string; status: string }
interface Q { id: number; title: string; category: string; difficulty: number }

/** 考试列表 */
const exams = ref<ExamItem[]>([])
/** 全部题库题目（供手动组卷） */
const questions = ref<Q[]>([])

/** 加载考试列表 */
async function loadExams() {
  const r = await listExams(1, 100)
  if (r.ok) exams.value = r.data
}

/** 页面初始化：加载考试列表 + 全部题目 */
onMounted(async () => {
  loadExams()
  const r = await listQuestions(1, 1000)
  if (r.ok) questions.value = r.data
  const cr = await getCategories()
  if (cr.ok) categories.value = cr.data
})

/* ---- 下发考试相关 ---- */
const filterCat = ref('')           // 手动组卷的分类筛选关键词
const examName = ref('')            // 考试名称
const selectedIds = ref<number[]>([]) // 手动组卷已选的题目ID
const showCreate = ref(false)       // 是否显示下发弹窗
const delTarget = ref<ExamItem | null>(null) // 待删除的考试
const createMode = ref<'manual' | 'auto'>('manual') // 当前组卷模式
const autoCount = ref(10)           // 自动组卷题目数量
const autoCategory = ref('')        // 自动组卷分类筛选
const autoDifficulty = ref(0)       // 自动组卷难度筛选（0=全部）
const duration = ref(0)             // 考试限时（分钟）
const useGA = ref(false)            // 是否使用遗传算法优化组卷
const categories = ref<string[]>([]) // 题库所有分类
const showDisc = ref(null as any)   // 区分度弹窗关联的考试
const discList = ref<any[]>([])     // 区分度数据列表

/* ---- 成绩查看相关 ---- */
const resultExam = ref<ExamItem | null>(null) // 当前查看的考试
const results = ref<any[]>([])       // 成绩列表
const resultPage = ref(1)            // 成绩分页当前页码
const resultTotal = ref(0)           // 成绩总数
const resultPageSize = 10
const resultTotalPages = computed(() => Math.ceil(resultTotal.value / resultPageSize) || 1)
const detailData = ref<any>()        // 答卷详情数据

/** 手动组卷的分类筛选（计算属性） */
const filteredQuestions = computed(() => {
  if (!filterCat.value) return questions.value
  return questions.value.filter(q => q.category === filterCat.value)
})

/** 手动下发考试 */
async function create() {
  if (!examName.value) return
  await createExam(examName.value, selectedIds.value, duration.value)
  closeCreate()
  loadExams()
}

/** 自动组卷并下发 */
async function autoCreate() {
  if (!examName.value) return
  if (!autoCount.value || autoCount.value <= 0) return alert('请输入有效的题目数量')
  const r = await autoGenerateExam(examName.value, autoCount.value, autoCategory.value || undefined,
    autoDifficulty.value > 0 ? autoDifficulty.value : undefined,
    duration.value, useGA.value)
  if (!r.ok) { alert(r.msg); return }
  closeCreate()
  loadExams()
}

/** 关闭下发弹窗并重置表单 */
function closeCreate() {
  showCreate.value = false
  examName.value = ''
  selectedIds.value = []
  filterCat.value = ''
  createMode.value = 'manual'
  autoCount.value = 10
  autoCategory.value = ''
  autoDifficulty.value = 0
  duration.value = 0
  useGA.value = false
}

/** 打开某场考试的成绩列表 */
async function viewResults(e: ExamItem) {
  resultExam.value = e
  resultPage.value = 1
  await loadResults()
}

/** 加载成绩列表（分页） */
async function loadResults() {
  if (!resultExam.value) return
  const r = await listExamRecords(resultExam.value.id, resultPage.value, resultPageSize)
  if (r.ok) { results.value = r.data; resultTotal.value = r.total }
}

/** 成绩分页跳转 */
function goResultPage(p: number) { resultPage.value = p; loadResults() }

/** 查看某份答卷的详细内容 */
async function viewDetail(recordId: number) {
  const r = await getRecordDetail(recordId)
  if (r.ok) detailData.value = r.data
}

/** 打开区分度分析弹窗 */
async function viewDiscrimination(e: ExamItem) {
  showDisc.value = e
  const r = await getDiscrimination(e.id)
  if (r.ok) discList.value = r.data
  else discList.value = []
}

/** 区分度质量 → CSS 类名映射 */
function qualityClass(q: string): string {
  if (q === '良好' || q === '优秀') return 'good'
  if (q === '一般') return 'medium'
  return 'poor'
}

/** 打开删除确认弹窗 */
function confirmDelete(e: ExamItem) { delTarget.value = e }

/** 结束考试 */
async function doFinish(e: ExamItem) {
  if (!confirm(`确定要结束考试 "${e.examName}" 吗？结束后学生将无法进入。`)) return
  await finishExam(e.id)
  loadExams()
}

/** 执行删除考试 */
async function doDelete() {
  if (!delTarget.value) return
  await deleteExam(delTarget.value.id)
  delTarget.value = null
  loadExams()
}
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px }
.tip { color: #888; font-size: 14px }
.btn-primary { background: #4f46e5; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.btn-del { background: none; color: #e53e3e; border: 1px solid #e53e3e; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; margin-left: 8px }
.btn-finish { background: none; color: #d69e2e; border: 1px solid #d69e2e; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; margin-left: 8px }
.btn-finish:hover { background: #fffbeb }
.btn-danger { background: #e53e3e; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.btn-action { background: none; color: #4f46e5; border: 1px solid #4f46e5; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; margin-left: 8px }
.btn-export { background: none; color: #38a169; border: 1px solid #38a169; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; text-decoration: none; margin-left: 8px; display: inline-block }
.col-act { white-space: nowrap }
table { width: 100%; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1) }
th, td { padding: 10px 12px; text-align: left; font-size: 14px; border-bottom: 1px solid #f0f0f0; vertical-align: middle }
th { background: #fafafa; font-weight: 600 }
.empty { text-align: center; color: #999; padding: 40px }
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px; font-size: 14px; color: #555 }
.pager button { padding: 6px 16px; border: 1px solid #ddd; border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px }
.pager button:disabled { opacity: .4; cursor: not-allowed }
.pager button:hover:not(:disabled) { background: #f5f5f5 }
.modal { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 100 }
.modal-box { background: #fff; border-radius: 12px; padding: 24px; max-height: 80vh; overflow-y: auto }
.modal-box h3 { margin-bottom: 16px }
.modal-box label { display: block; margin-bottom: 10px; font-size: 14px }
.modal-box input { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 6px; margin-top: 4px }
.modal-btn { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px }
.modal-btn button { padding: 8px 20px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer }
.modal-btn .btn-primary { background: #4f46e5; color: #fff; border: none }
.modal-btn .btn-danger { background: #e53e3e; color: #fff; border: none }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px }
.filter-input { flex: 1; padding: 6px 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 13px }
.q-list { max-height: 300px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; margin-bottom: 12px }
.q-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid #f5f5f5; cursor: pointer; font-size: 13px }
.q-item:hover { background: #fafafa }
.q-item.selected { background: #eef2ff }
.q-item input { width: auto; margin: 0 }
.q-title-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.q-cat { color: #888; font-size: 12px; flex-shrink: 0 }
.q-diff { color: #d69e2e; font-size: 11px; flex-shrink: 0; margin-left: 4px }
.score-line { color: #666; font-size: 14px; margin-bottom: 16px }
.d-card { border: 1px solid #e0e0e0; border-radius: 8px; padding: 12px; margin-bottom: 8px; position: relative }
.d-card.wrong { border-color: #fecaca; background: #fef2f2 }
.d-title { font-weight: 600; margin-bottom: 4px; font-size: 14px }
.d-answer { font-size: 13px; margin-bottom: 2px }
.correct-show { margin-left: 20px; color: #38a169; font-weight: 500 }
.d-score { position: absolute; right: 12px; top: 12px; font-size: 13px; font-weight: 600 }
.wrong .d-score { color: #e53e3e }
.del-msg { font-size: 14px; color: #555; margin-bottom: 8px }
.mode-tabs { display: flex; gap: 0; margin-bottom: 16px; border: 1px solid #ddd; border-radius: 8px; overflow: hidden }
.mode-tabs button { flex: 1; padding: 8px 0; border: none; background: #f5f5f5; cursor: pointer; font-size: 14px; transition: background .2s }
.mode-tabs button.active { background: #4f46e5; color: #fff; font-weight: 600 }
.mode-tabs button:not(.active):hover { background: #e8e8e8 }
.clamp-text { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.quality-good { color: #38a169; font-weight: 600 }
.quality-medium { color: #d69e2e; font-weight: 600 }
.quality-poor { color: #e53e3e; font-weight: 600 }
.tag-ongoing { background: #dcfce7; color: #16a34a; font-size: 12px; padding: 2px 8px; border-radius: 10px; font-weight: 500; display: inline-block; vertical-align: middle; line-height: 1.5 }
.tag-finished { background: #f3f4f6; color: #9ca3af; font-size: 12px; padding: 2px 8px; border-radius: 10px; font-weight: 500; display: inline-block; vertical-align: middle; line-height: 1.5 }
</style>

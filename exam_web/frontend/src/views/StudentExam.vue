<!--
  StudentExam.vue —— 学生端核心页面
  两大模式切换：考试模式 / 练习模式
  
  考试模式功能：
    1. 学习统计卡片（考试次数、平均分、错题量、正确率）
    2. 待参加考试列表 → 点击进入答题
    3. 已完成考试列表 → 查看答卷详情
    4. 错题本（按考试筛选）
    5. 历次成绩趋势柱状图
    6. 答题页：选择题/判断题/填空题三种题型，支持倒计时自动交卷
  
  练习模式功能：
    1. 练习设置（题型、分类、顺序/随机模式）
    2. 逐题作答 + 即时判对错反馈
    3. 练习结果统计（正确数/正确率）
-->
<template>
  <div class="student-dash">
    <!-- 模式切换标签 -->
    <div v-if="!examData" class="tab-bar">
      <button :class="{ active: activeTab === 'exam' }" @click="activeTab = 'exam'">考试</button>
      <button :class="{ active: activeTab === 'practice' }" @click="activeTab = 'practice'">练习</button>
      <button :class="{ active: activeTab === 'wiki' }" @click="activeTab = 'wiki'; loadWiki()">知识图谱</button>
    </div>

    <!-- ==================== 考试模式 ==================== -->
    <template v-if="activeTab === 'exam'">
      <!-- 倒计时栏 -->
      <div v-if="examData && examDuration > 0" class="countdown-bar" :class="{ urgent: remainingSeconds < 60 }">
        剩余时间: {{ formatTime(remainingSeconds) }}
      </div>

      <!-- 仪表盘（非答题状态） -->
      <template v-if="!examData">
        <div v-if="Object.keys(dashboardErrors).length" class="card" aria-live="polite">
          <div v-for="(label, panel) in dashboardErrors" :key="panel">
            {{ label }}加载失败
            <button type="button" class="btn-review" @click="dashboardLoaders[panel]()">
              重试{{ label }}
            </button>
          </div>
        </div>

        <!-- 模块1：学习统计卡片 -->
        <div v-if="!dashboardErrors.stats" class="stats-row">
          <div class="stat-card">
            <div class="stat-icon exams-icon">&#9998;</div>
            <div class="stat-num">{{ stats.examCount }}</div>
            <div class="stat-label">已考次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon avg-icon">&#9733;</div>
            <div class="stat-num">{{ stats.avgScore }}</div>
            <div class="stat-label">平均分</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon wrong-icon">&#9888;</div>
            <div class="stat-num">{{ stats.wrongCount }}</div>
            <div class="stat-label">错题量</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon acc-icon">&#10004;</div>
            <div class="stat-num">{{ stats.accuracy }}%</div>
            <div class="stat-label">答题正确率</div>
          </div>
        </div>

        <!-- 模块2a：待参加考试 -->
        <div class="card">
          <div class="card-title">可参加考试</div>
          <table v-if="pendingList.length">
            <thead><tr><th>#</th><th>考试名称</th><th>题目数</th><th>限时</th><th>下发时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(e, i) in pendingList" :key="e.id">
                <td>{{ i + 1 }}</td>
                <td>{{ e.examName }}</td>
                <td>{{ e.questionCount }} 题</td>
                <td>{{ e.duration > 0 ? e.duration + '分钟' : '不限时' }}</td>
                <td>{{ e.createTime }}</td>
                <td><button class="btn-enter" @click="enterExam(e)">进入考试</button></td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="!dashboardErrors.exams" class="card-empty">暂无新考试，请等待管理员下发</p>
        </div>

        <!-- 模块2b：已完成考试 -->
        <div class="card">
          <div class="card-title">已完成考试</div>
          <table v-if="doneList.length">
            <thead><tr><th>#</th><th>考试名称</th><th>题目数</th><th>下发时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(e, i) in doneList" :key="e.id" class="done-row">
                <td>{{ i + 1 }}</td>
                <td>{{ e.examName }}</td>
                <td>{{ e.questionCount }} 题</td>
                <td>{{ e.createTime }}</td>
                <td><button class="btn-review" @click="viewRecord(e)">查看答卷</button></td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="!dashboardErrors.exams" class="card-empty">暂无已完成的考试</p>
        </div>

        <!-- 模块3：错题本 -->
        <div class="card">
          <div class="card-title">
            错题本
            <select v-model="wrongFilter" @change="loadWrongQuestions" class="filter-select">
              <option value="">全部考试</option>
              <option v-for="e in examOptions" :key="e.id" :value="e.id">{{ e.name }}</option>
            </select>
            <button v-if="filteredWrongList.length" class="btn-analyze-all" :disabled="analyzingAll" @click="analyzeAllWrong">
              {{ analyzingAll ? '解析中...' : '一键解析全部' }}
            </button>
            <label class="show-killed-label" v-if="masteredIds.size > 0">
              <input type="checkbox" v-model="showKilled" />
              显示已Kill ({{ masteredIds.size }})
            </label>
          </div>
          <table v-if="displayWrongList.length">
            <thead><tr><th>#</th><th>题目</th><th>正确答案</th><th>你的答案</th><th>分类</th><th>错题时间</th><th>AI 解析</th></tr></thead>
            <tbody>
              <template v-for="(w, i) in displayWrongList" :key="w.questionId + '-' + w.examName">
                <tr :class="{ 'row-killed': masteredIds.has(w.questionId) }">
                  <td class="col-seq">{{ i + 1 }}</td>
                  <td class="title-col-wrong" @click="showWrongDetail(w)">{{ w.title }}</td>
                  <td class="correct-ans">{{ w.correctAnswer }}</td>
                  <td class="wrong-ans">{{ w.studentAnswer }}</td>
                  <td class="col-cat">{{ w.category }}</td>
                  <td class="col-time">{{ w.submitTime }}</td>
                  <td class="col-act">
                    <button class="btn-analysis" @click="toggleAnalysis(w.questionId)">
                      {{ analysisMap[w.questionId] ? '收起' : 'AI 解析' }}
                    </button>
                    <button class="btn-kill" :class="{ killed: masteredIds.has(w.questionId) }" @click="toggleKill(w.questionId)">
                      {{ masteredIds.has(w.questionId) ? '恢复' : 'Kill' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="analysisMap[w.questionId]" class="analysis-row">
                  <td colspan="7">
                    <div class="analysis-panel">
                      <div v-if="analysisMap[w.questionId].loading" class="analysis-loading">AI 正在分析中...</div>
                      <template v-else>
                        <div class="analysis-section">
                          <span class="analysis-label">解析</span>
                          <p>{{ analysisMap[w.questionId].analysis }}</p>
                        </div>
                        <div v-if="analysisMap[w.questionId].knowledgePoints" class="analysis-section">
                          <span class="analysis-label">知识点</span>
                          <p class="kp-tags">
                            <span v-for="kp in analysisMap[w.questionId].knowledgePoints.split(',')" :key="kp" class="kp-tag">{{ kp.trim() }}</span>
                          </p>
                        </div>
                        <div v-if="analysisMap[w.questionId].studySuggestion" class="analysis-section">
                          <span class="analysis-label">学习建议</span>
                          <p>{{ analysisMap[w.questionId].studySuggestion }}</p>
                        </div>
                      </template>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
          <p v-else-if="!dashboardErrors.wrong" class="card-empty">恭喜你，暂时没有错题</p>
        </div>

        <!-- 错题详情弹窗 -->
        <div v-if="wrongDetail" class="modal" @click.self="wrongDetail = null">
          <div class="modal-box detail-modal">
            <h3>错题详情</h3>
            <div class="detail-block">
              <p class="detail-label">题干</p>
              <p class="detail-text">{{ wrongDetail.title }}</p>
            </div>
            <div class="detail-block">
              <p class="detail-label">选项</p>
              <p class="detail-text">A. {{ wrongDetail.optionA }}</p>
              <p class="detail-text">B. {{ wrongDetail.optionB }}</p>
              <p class="detail-text">C. {{ wrongDetail.optionC }}</p>
              <p class="detail-text">D. {{ wrongDetail.optionD }}</p>
            </div>
            <div class="detail-row">
              <span class="detail-label">正确答案：</span><strong class="correct-ans">{{ wrongDetail.correctAnswer }}</strong>
              <span style="margin-left:24px" class="detail-label">你的答案：</span><strong class="wrong-ans">{{ wrongDetail.studentAnswer }}</strong>
            </div>
            <div class="detail-row">
              <span class="detail-label">分类：</span><span>{{ wrongDetail.category }}</span>
              <span style="margin-left:24px" class="detail-label">所属考试：</span><span>{{ wrongDetail.examName }}</span>
            </div>
            <div class="modal-btn">
              <button @click="wrongDetail = null">关闭</button>
            </div>
          </div>
        </div>

        <!-- 模块4：成绩图表 -->
        <div v-if="scoreList.length" class="card">
          <div class="card-title">历次考试得分趋势</div>
          <div class="chart">
            <div v-for="s in scoreList" :key="s.submitTime" class="bar-col">
              <div class="bar" :style="{ height: barHeight(s.score) }">
                <span class="bar-val">{{ s.score }}</span>
              </div>
              <div class="bar-name">{{ s.examName }}</div>
            </div>
          </div>
        </div>
      </template>

      <!-- ========== 答题页 ========== -->
      <div v-if="examData" class="exam-panel card">
        <h2>{{ examData.examName }}</h2>
        <div v-for="(q, i) in examData.data" :key="q.id" class="q-card">
          <div class="q-title">
            {{ i + 1 }}. {{ q.title }}
            <span class="tag">{{ q.category }}</span>
          </div>
          <div class="options">
            <label v-for="opt in ['A','B','C','D']" :key="opt" :class="{ chosen: answers[q.id] === opt }">
              <input type="radio" :name="'q'+q.id" :value="opt" v-model="answers[q.id]" />{{ opt }}. {{ q['option'+opt] }}
            </label>
          </div>
        </div>
        <div class="submit-bar">
          <button class="btn-submit" @click="confirmSubmit = true">确认交卷</button>
        </div>
      </div>

      <!-- 交卷确认弹窗 -->
      <div v-if="confirmSubmit" class="modal" @click.self="confirmSubmit = false">
        <div class="modal-box"><h3>确认交卷</h3><p class="del-msg">交卷后将无法修改，确定提交？</p>
          <div class="modal-btn"><button @click="confirmSubmit = false">取消</button><button class="btn-primary" @click="doSubmit">确认交卷</button></div>
        </div>
      </div>

      <!-- 成绩弹窗 -->
      <div v-if="resultScore !== null" class="modal">
        <div class="modal-box" style="text-align:center"><h3>交卷成功</h3>
          <p class="big-score">得分: {{ resultScore }} / {{ totalQuestions }}</p>
          <div class="modal-btn" style="justify-content:center"><button class="btn-primary" @click="backToList">返回列表</button></div>
        </div>
      </div>

      <!-- 查看答卷弹窗 -->
      <div v-if="reviewData" class="modal" @click.self="reviewData = null">
        <div class="modal-box review-modal">
          <h3>{{ reviewData.examName }} - 答卷</h3>
          <p class="review-score">得分：{{ reviewData.score }} / {{ reviewData.totalQuestion }}</p>
          <div v-for="(q, i) in reviewData.questions" :key="q.id" class="rq-card" :class="{ rqCorrect: q.correct, rqWrong: !q.correct }">
            <div class="rq-title">{{ i + 1 }}. {{ q.title }}</div>
            <div class="rq-opts">
              <span v-for="opt in ['A','B','C','D']" :key="opt"
                :class="{ chosen: q.studentAnswer === opt, correctHighlight: q.correctAnswer === opt }">
                {{ opt }}. {{ q['option' + opt] }}
              </span>
            </div>
            <div class="rq-result">
              你的答案：<span :class="q.correct ? 'rg' : 'rw'">{{ q.studentAnswer || '未作答' }}</span>
              正确答案：<span class="rc">{{ q.correctAnswer }}</span>
            </div>
          </div>
          <div class="modal-btn" style="justify-content:center"><button @click="reviewData = null">关闭</button></div>
        </div>
      </div>
    </template>

    <!-- ==================== 练习模式 ==================== -->
    <template v-if="activeTab === 'practice'">
      <!-- 练习设置面板 -->
      <div class="card">
        <div class="card-title">练习设置</div>
        <div class="practice-controls">
          <div class="ctrl-group">
            <label>题型：</label>
            <select v-model="practiceType">
              <option value="">全部</option>
              <option value="choice">单选题</option>
            </select>
          </div>
          <div class="ctrl-group">
            <label>分类：</label>
            <select v-model="practiceCategory">
              <option value="">全部分类</option>
              <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </div>
          <div class="ctrl-group">
            <label>模式：</label>
            <select v-model="practiceMode">
              <option value="seq">顺序练习</option>
              <option value="random">随机练习</option>
            </select>
          </div>
          <button class="btn-enter" @click="startPractice">开始练习</button>
        </div>
      </div>

      <!-- 练习答题区 -->
      <div v-if="practiceQuestions.length > 0 && !practiceFinished" class="card">
        <div class="practice-progress">
          <div class="progress-text">{{ practiceIndex + 1 }} / {{ practiceQuestions.length }}</div>
          <div class="progress-bar-track">
            <div class="progress-bar-fill" :style="{ width: ((practiceIndex + 1) / practiceQuestions.length * 100) + '%' }"></div>
          </div>
        </div>
        <div class="q-card" v-if="currentPracticeQ">
          <div class="q-title">
            {{ practiceIndex + 1 }}. {{ currentPracticeQ.title }}
            <span class="tag">{{ currentPracticeQ.category || '' }}</span>
          </div>

          <div class="options">
            <label v-for="opt in ['A','B','C','D']" :key="opt"
              :class="practiceOptionClass(opt)"
              @click="!practiceSubmitted ? selectPracticeAnswer(opt) : null">
              <input type="radio" :name="'pq'+currentPracticeQ.id" :value="opt" :disabled="practiceSubmitted"
                :checked="practiceAnswer === opt" />{{ opt }}. {{ currentPracticeQ['option' + opt] }}
            </label>
          </div>

          <!-- 即时反馈 -->
          <div v-if="practiceSubmitted" class="practice-feedback" :class="{ correct: practiceCurrentCorrect, wrong: !practiceCurrentCorrect }">
            <span v-if="practiceCurrentCorrect">&#10004; 回答正确！</span>
            <span v-else>&#10008; 回答错误！正确答案：<strong>{{ currentPracticeQ.answer }}</strong></span>
          </div>

          <div v-if="practiceSubmitted" class="practice-next-bar">
            <button class="btn-enter" @click="nextPracticeQuestion">
              {{ practiceIndex + 1 < practiceQuestions.length ? '下一题' : '查看结果' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 练习结果 -->
      <div v-if="practiceFinished" class="card">
        <div class="card-title">练习结果</div>
        <div class="practice-result">
          <div class="result-big">{{ practiceCorrectCount }} / {{ practiceQuestions.length }}</div>
          <div class="result-sub">正确率：{{ practiceQuestions.length > 0 ? Math.round(practiceCorrectCount / practiceQuestions.length * 100) : 0 }}%</div>
          <div class="practice-next-bar">
            <button class="btn-enter" @click="resetPractice">再来一组</button>
          </div>
        </div>
      </div>

      <p v-if="practiceQuestions.length === 0 && !practiceFinished && !practiceLoading" class="card-empty">
        请设置题型和模式，点击"开始练习"
      </p>
      <p v-if="practiceLoading" class="card-empty">加载中...</p>
    </template>

    <!-- ==================== 知识图谱模式 ==================== -->
    <template v-if="activeTab === 'wiki'">
      <div class="card">
        <div class="card-title">
          个人学习 Wiki
          <button class="btn-analyze-all" :disabled="wikiLoading" @click="doRebuildWiki" style="margin-left:12px">
            {{ wikiLoading ? '生成中...' : '重建知识图谱' }}
          </button>
          <button v-if="wikiData.length" class="btn-analyze-all" :disabled="reportLoading" @click="doWeeklyReport" style="margin-left:8px;background:#8b5cf6">
            {{ reportLoading ? '生成中...' : '学习周报' }}
          </button>
        </div>

        <p v-if="!wikiData.length && !wikiLoading" class="card-empty">
          还没有知识图谱，点击"重建知识图谱"让 AI 分析你的学习数据
        </p>
        <p v-if="wikiLoading" class="card-empty">AI 正在分析你的答题数据...</p>

        <div v-if="wikiData.length" class="wiki-grid">
          <div v-for="item in wikiData" :key="item.conceptName" class="wiki-card" :class="'wiki-level-' + item.masteryLevel">
            <div class="wiki-header">
              <span class="wiki-name">{{ item.conceptName }}</span>
              <span class="wiki-stars">{{ '★'.repeat(item.masteryLevel) }}{{ '☆'.repeat(5 - item.masteryLevel) }}</span>
            </div>
            <div class="wiki-stats">
              错 {{ item.wrongCount }} / {{ item.totalCount }} 题
            </div>
            <div class="wiki-note" v-if="item.note">{{ item.note }}</div>
            <div class="wiki-date">更新于 {{ item.lastReviewTime }}</div>
          </div>
        </div>
      </div>

      <!-- 学习周报弹窗 -->
      <div v-if="weeklyReport" class="modal" @click.self="weeklyReport = null">
        <div class="modal-box report-modal">
          <h3>学习周报</h3>
          <div class="report-summary">{{ weeklyReport.summary }}</div>

          <div v-if="weeklyReport.strengths && weeklyReport.strengths.length" class="report-section">
            <span class="report-label r-green">强项</span>
            <ul><li v-for="s in weeklyReport.strengths" :key="s">{{ s }}</li></ul>
          </div>

          <div v-if="weeklyReport.weakPoints && weeklyReport.weakPoints.length" class="report-section">
            <span class="report-label r-red">薄弱点</span>
            <ul><li v-for="w in weeklyReport.weakPoints" :key="w">{{ w }}</li></ul>
          </div>

          <div v-if="weeklyReport.suggestion" class="report-section">
            <span class="report-label r-blue">学习建议</span>
            <p>{{ weeklyReport.suggestion }}</p>
          </div>

          <div class="modal-btn">
            <button @click="weeklyReport = null">关闭</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { getStudentExams, getExamQuestions, submitExam, getStudentStats, getWrongQuestions, getScoreHistory, getStudentRecordDetail, getPracticeQuestions, getCategories, analyzeWrongQuestions, masterQuestion, unmasterQuestion, getMasteredQuestions, getKnowledgeMap, rebuildKnowledgeMap, getWeeklyReport } from '../api'

/** 考试项数据结构 */
interface ExamItem { id: number; examName: string; questionCount: number; createTime: string; submitted: boolean; duration: number }
/** 题目数据结构 */
interface Q { id: number; title: string; optionA: string; optionB: string; optionC: string; optionD: string; category: string; type?: string; answer?: string }
/** 错题项数据结构 */
interface WrongItem { questionId: number; title: string; examName: string; correctAnswer: string; studentAnswer: string; submitTime: string; optionA?: string; optionB?: string; optionC?: string; optionD?: string; category?: string; difficulty?: number }
/** 练习题目数据结构 */
interface PracticeQ { id: number; title: string; type: string; optionA?: string; optionB?: string; optionC?: string; optionD?: string; answer: string; category?: string }

/** 当前标签页：exam=考试 | practice=练习 */
const activeTab = ref('exam')

/* ---- 考试模式仪表盘数据 ---- */
const exams = ref<ExamItem[]>([])                        // 全部考试列表
const stats = ref({ examCount: 0, avgScore: 0, wrongCount: 0, accuracy: 0 })  // 学习统计
const wrongList = ref<WrongItem[]>([])                   // 错题列表
const scoreList = ref<{ examName: string; score: number; submitTime: string }[]>([])  // 历次得分
const wrongFilter = ref('')                              // 错题本筛选条件

const analyzingAll = ref(false)
const analysisMap = ref<Record<number, { analysis: string; knowledgePoints: string; studySuggestion: string; loading: boolean }>>({})

const wrongDetail = ref<WrongItem | null>(null)
function showWrongDetail(w: WrongItem) { wrongDetail.value = w }

const masteredIds = ref<Set<number>>(new Set())

async function loadMastered() {
  const ids = await getMasteredQuestions()
  masteredIds.value = new Set(ids)
}

async function toggleKill(qid: number) {
  if (masteredIds.value.has(qid)) {
    await unmasterQuestion(qid)
    masteredIds.value.delete(qid)
  } else {
    await masterQuestion(qid)
    masteredIds.value.add(qid)
  }
  masteredIds.value = new Set(masteredIds.value)
}

interface WikiItem { conceptName: string; masteryLevel: number; wrongCount: number; totalCount: number; note: string; lastReviewTime: string }
interface WeeklyReport { summary: string; strengths: string[]; weakPoints: string[]; suggestion: string }

const wikiData = ref<WikiItem[]>([])
const wikiLoading = ref(false)
const reportLoading = ref(false)
const weeklyReport = ref<WeeklyReport | null>(null)

async function loadWiki() {
  const r = await getKnowledgeMap()
  wikiData.value = r.data || []
}

async function doRebuildWiki() {
  wikiLoading.value = true
  wikiData.value = []
  try {
    const r = await rebuildKnowledgeMap()
    if (r && r.ok) wikiData.value = r.data || []
    else alert(r?.msg || '请求失败，请检查网络或后端是否启动')
  } catch (e) {
    alert('请求异常：' + (e as Error).message)
  }
  wikiLoading.value = false
}

async function doWeeklyReport() {
  reportLoading.value = true
  try {
    const r = await getWeeklyReport()
    if (r && r.ok) weeklyReport.value = r.data
    else alert(r?.msg || '请求失败')
  } catch (e) {
    alert('请求异常：' + (e as Error).message)
  }
  reportLoading.value = false
}

const filteredWrongList = computed(() => wrongList.value.filter(w => !masteredIds.value.has(w.questionId)))

const showKilled = ref(false)
const displayWrongList = computed(() => showKilled.value ? wrongList.value : filteredWrongList.value)

/** 答题中状态 */
const examData = ref<{ examName: string; data: Q[] } | null>(null)  // 当前考试数据（null=仪表盘，有值=答题中）
const currentExamId = ref(0)                             // 正在作答的考试ID
const answers = ref<Record<number, string>>({})          // 学生答案 { 题目ID: 答案选项 }
const confirmSubmit = ref(false)                         // 交卷确认弹窗
const resultScore = ref<number | null>(null)             // 交卷后得分
const totalQuestions = ref(0)                            // 总题数

/** 答卷查看数据 */
const reviewData = ref<any>(null)

/* ---- 倒计时 ---- */
const remainingSeconds = ref(0)                          // 剩余秒数
const examDuration = ref(0)                              // 考试限时（分钟）
let countdownTimer: ReturnType<typeof setInterval> | null = null

/* ---- 练习模式数据 ---- */
const practiceType = ref('')                             // 筛选题型：choice/judge/fill
const practiceCategory = ref('')                         // 筛选分类关键词
const practiceMode = ref('seq')                          // 练习模式：seq=顺序 | random=随机
const practiceQuestions = ref<PracticeQ[]>([])           // 练习题目列表
const practiceIndex = ref(0)                             // 当前题目索引
const practiceAnswer = ref('')                           // 当前题目的学生回答
const practiceSubmitted = ref(false)                     // 是否已提交判分
const practiceCurrentCorrect = ref(false)                // 判分结果：正确/错误
const practiceFinished = ref(false)                      // 练习是否结束
const practiceCorrectCount = ref(0)                      // 答对题数
const practiceLoading = ref(false)

const categories = ref<string[]>([])                       // 加载中

/* ---- 计算属性 ---- */
/** 待参加考试（未交卷） */
const pendingList = computed(() => exams.value.filter(e => !e.submitted))
/** 已完成考试（已交卷） */
const doneList = computed(() => exams.value.filter(e => e.submitted))
/** 错题本考试筛选选项（去重） */
const examOptions = computed(() => {
  const set = new Set<string>()
  return exams.value.filter(e => e.submitted && !set.has(e.examName) && set.add(e.examName))
    .map(e => ({ id: e.id, name: e.examName }))
})

/** 当前练习题目 */
const currentPracticeQ = computed(() => {
  if (practiceIndex.value < practiceQuestions.value.length) {
    return practiceQuestions.value[practiceIndex.value]
  }
  return null
})

/** 柱状图高度：按最高分等比映射 */
function barHeight(s: number) {
  const max = Math.max(...scoreList.value.map(x => x.score), 1)
  return Math.max(10, (s / max) * 140) + 'px'
}

const dashboardLabels = { exams: '考试列表', stats: '学习统计', wrong: '错题本', history: '成绩历史' }
type DashboardPanel = keyof typeof dashboardLabels
const dashboardErrors = ref<Partial<Record<DashboardPanel, string>>>({})
const dashboardRevisions: Record<DashboardPanel, number> = { exams: 0, stats: 0, wrong: 0, history: 0 }

async function loadDashboardPanel<T>(
  panel: DashboardPanel,
  request: () => Promise<{ ok: boolean; data: T }>,
  apply: (data: T) => void,
) {
  const revision = ++dashboardRevisions[panel]
  try {
    const result = await request()
    if (revision !== dashboardRevisions[panel]) return
    if (!result.ok) {
      dashboardErrors.value[panel] = dashboardLabels[panel]
      return
    }
    apply(result.data)
    delete dashboardErrors.value[panel]
  } catch {
    if (revision === dashboardRevisions[panel]) {
      dashboardErrors.value[panel] = dashboardLabels[panel]
    }
  }
}

function loadWrongQuestions() {
  const examId = wrongFilter.value ? Number(wrongFilter.value) : undefined
  return loadDashboardPanel<WrongItem[]>('wrong', () => getWrongQuestions(examId), data => { wrongList.value = data })
}

const dashboardLoaders = {
  exams: () => loadDashboardPanel<ExamItem[]>('exams', getStudentExams, data => { exams.value = data }),
  stats: () => loadDashboardPanel<typeof stats.value>('stats', getStudentStats, data => { stats.value = data }),
  wrong: loadWrongQuestions,
  history: () => loadDashboardPanel<typeof scoreList.value>('history', getScoreHistory, data => { scoreList.value = data }),
}

function loadAll() {
  return Promise.all(Object.values(dashboardLoaders).map(load => load()))
}

async function analyzeOne(qid: number) {
  analysisMap.value[qid] = { analysis: '', knowledgePoints: '', studySuggestion: '', loading: true }
  const r = await analyzeWrongQuestions([qid])
  if (r.ok && r.data && r.data.length > 0) {
    const item = r.data[0]
    analysisMap.value[qid] = {
      analysis: item.analysis || '',
      knowledgePoints: item.knowledgePoints || '',
      studySuggestion: item.studySuggestion || '',
      loading: false
    }
    if (item.fromCache) {
      analysisMap.value[qid].analysis += ' (缓存)'
    }
  } else {
    analysisMap.value[qid] = { analysis: '解析失败', knowledgePoints: '', studySuggestion: '', loading: false }
  }
}

async function analyzeAllWrong() {
  if (wrongList.value.length === 0) return
  analyzingAll.value = true
  const ids = [...new Set(wrongList.value.map(w => w.questionId))]
  for (const qid of ids) {
    if (!analysisMap.value[qid]) {
      analysisMap.value[qid] = { analysis: '', knowledgePoints: '', studySuggestion: '', loading: true }
    }
  }
  const r = await analyzeWrongQuestions(ids)
  if (r.ok && r.data) {
    for (const item of r.data) {
      analysisMap.value[item.questionId] = {
        analysis: item.analysis || '',
        knowledgePoints: item.knowledgePoints || '',
        studySuggestion: item.studySuggestion || '',
        loading: false
      }
    }
  }
  analyzingAll.value = false
}

function toggleAnalysis(qid: number) {
  if (!analysisMap.value[qid]) {
    analyzeOne(qid)
  } else {
    delete analysisMap.value[qid]
  }
}

async function loadCategories() {
  const r = await getCategories()
  if (r.ok) categories.value = r.data
}

onMounted(() => {
    loadAll()
    loadCategories()
    loadMastered()
  })

  onUnmounted(() => {
    stopCountdown()
  })

/* ---- 倒计时功能 ---- */
/** 停止倒计时定时器 */
function stopCountdown() {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 启动倒计时：每秒减1，归零后自动交卷 */
function startCountdown(durationMinutes: number) {
  stopCountdown()
  remainingSeconds.value = durationMinutes * 60
  countdownTimer = setInterval(() => {
    remainingSeconds.value--
    if (remainingSeconds.value <= 0) {
      stopCountdown()
      doSubmit()
    }
  }, 1000)
}

/** 秒数格式化为 MM:SS */
function formatTime(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

/* ---- 考试操作 ---- */
/** 进入考试：加载题目 → 初始化答案 → 启动倒计时 */
async function enterExam(e: ExamItem) {
  const r = await getExamQuestions(e.id)
  if (r.ok) {
    currentExamId.value = e.id
    examData.value = { examName: r.examName, data: r.data }
    answers.value = {}
    r.data.forEach((q: Q) => { answers.value[q.id] = '' })
    examDuration.value = e.duration || 0
    if (examDuration.value > 0) {
      startCountdown(examDuration.value)
    }
  }
}

/** 交卷：将数字键转换为字符串键 → 提交后端 → 显示得分 */
async function doSubmit() {
  if (!examData.value) return
  stopCountdown()
  const strAnswers: Record<string, string> = {}
  for (const [k, v] of Object.entries(answers.value)) strAnswers[String(k)] = v
  const r = await submitExam(currentExamId.value, strAnswers)
  confirmSubmit.value = false
  resultScore.value = r.score
  totalQuestions.value = r.total
}

/** 返回仪表盘：清除答题状态，重新加载数据 */
function backToList() {
  resultScore.value = null
  examData.value = null
  answers.value = {}
  examDuration.value = 0
  remainingSeconds.value = 0
  stopCountdown()
  loadAll()
}

/** 查看已完成考试的答卷详情 */
async function viewRecord(e: ExamItem) {
  const r = await getStudentRecordDetail(e.id)
  if (r.ok) reviewData.value = r.data
}

/* ---- 练习模式操作 ---- */
/** 开始练习：获取题目列表 */
async function startPractice() {
  practiceLoading.value = true
  practiceQuestions.value = []
  practiceIndex.value = 0
  practiceAnswer.value = ''
  practiceSubmitted.value = false
  practiceFinished.value = false
  practiceCorrectCount.value = 0
  const r = await getPracticeQuestions(
    practiceType.value || undefined,
    practiceCategory.value || undefined,
    practiceMode.value
  )
  practiceLoading.value = false
  if (r.ok && r.data && r.data.length > 0) {
    practiceQuestions.value = r.data
    practiceIndex.value = 0
    practiceAnswer.value = ''
    practiceSubmitted.value = false
  } else {
    alert('当前筛选条件下暂无对应习题，请更换分类/题型')
  }
}

/** 选择题/判断题：点击选项即提交判分 */
function selectPracticeAnswer(opt: string) {
  if (practiceSubmitted.value) return
  practiceAnswer.value = opt
  practiceSubmitted.value = true
  practiceCurrentCorrect.value = opt === currentPracticeQ.value?.answer
  if (practiceCurrentCorrect.value) {
    practiceCorrectCount.value++
  }
}

/** 下一题：索引+1，或显示最终结果 */
function nextPracticeQuestion() {
  if (practiceIndex.value + 1 < practiceQuestions.value.length) {
    practiceIndex.value++
    practiceAnswer.value = ''
    practiceSubmitted.value = false
  } else {
    practiceFinished.value = true
  }
}

/** 重置练习状态 */
function resetPractice() {
  practiceQuestions.value = []
  practiceIndex.value = 0
  practiceAnswer.value = ''
  practiceSubmitted.value = false
  practiceFinished.value = false
  practiceCorrectCount.value = 0
    practiceCategory.value = ''
}

/** 练习选项样式：提交前仅高亮选中项，提交后绿色=正确、红色=选错 */
function practiceOptionClass(opt: string) {
  if (!practiceSubmitted.value) return { chosen: practiceAnswer.value === opt }
  const correctAns = currentPracticeQ.value?.answer
  if (opt === correctAns) return { chosen: practiceAnswer.value === opt, correctHighlight: true }
  if (practiceAnswer.value === opt && opt !== correctAns) return { chosen: true, wrongHighlight: true }
  return {}
}
</script>

<style scoped>
.student-dash { max-width: 900px; margin: 0 auto }

/* 模式标签 */
.tab-bar { display: flex; gap: 0; margin-bottom: 20px; border-bottom: 2px solid #e5e7eb }
.tab-bar button {
  padding: 10px 28px; border: none; background: none; font-size: 15px; font-weight: 500;
  color: #888; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -2px; transition: .2s
}
.tab-bar button.active { color: #4f46e5; border-bottom-color: #4f46e5 }

/* 倒计时 */
.countdown-bar {
  text-align: center; padding: 10px; background: #4f46e5; color: #fff; border-radius: 8px;
  font-size: 16px; font-weight: 600; margin-bottom: 16px; transition: background .3s
}
.countdown-bar.urgent { background: #e53e3e; animation: pulse 1s infinite }
@keyframes pulse { 0%, 100% { opacity: 1 } 50% { opacity: 0.7 } }

/* 统计卡片 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px }
.stat-card { background: #fff; border-radius: 10px; padding: 20px 16px; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,.06) }
.stat-icon { font-size: 22px; margin-bottom: 6px }
.exams-icon { color: #4f46e5 }
.avg-icon { color: #e5a100 }
.wrong-icon { color: #e53e3e }
.acc-icon { color: #38a169 }
.stat-num { font-size: 26px; font-weight: 700; color: #1a1a2e }
.stat-label { font-size: 12px; color: #888; margin-top: 2px }

/* 卡片容器 */
.card { background: #fff; border-radius: 10px; padding: 16px 20px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06) }
.card-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; display: flex; align-items: center; gap: 12px }
.card-empty { text-align: center; color: #999; padding: 24px 0; font-size: 14px }

/* 筛选下拉 */
.filter-select { font-weight: 400; font-size: 13px; padding: 4px 8px; border: 1px solid #ddd; border-radius: 4px; margin-left: auto }
table { width: 100%; border-radius: 6px; overflow: hidden }
th, td { padding: 8px 10px; text-align: left; font-size: 13px; border-bottom: 1px solid #f0f0f0 }
th { background: #fafafa; font-weight: 600; color: #555 }
.done-row td { color: #999 }
.clamp-text { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.correct-ans { color: #38a169; font-weight: 600 }
.wrong-ans { color: #e53e3e; font-weight: 600 }
.btn-enter { background: #4f46e5; color: #fff; border: none; padding: 5px 14px; border-radius: 5px; cursor: pointer; font-size: 12px }
.btn-review { background: none; color: #4f46e5; border: 1px solid #4f46e5; padding: 5px 14px; border-radius: 5px; cursor: pointer; font-size: 12px }

/* 图表 */
.chart { display: flex; align-items: flex-end; gap: 24px; height: 180px; padding: 8px 0 }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100% }
.bar { width: 36px; background: linear-gradient(180deg, #4f46e5, #818cf8); border-radius: 4px 4px 0 0; min-height: 10px; position: relative; transition: .3s }
.bar-val { position: absolute; top: -20px; left: 50%; transform: translateX(-50%); font-size: 12px; font-weight: 600; color: #4f46e5 }
.bar-name { font-size: 11px; color: #888; margin-top: 6px; text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 70px }

/* 考试面板 */
.exam-panel { margin-top: 0 }
.exam-panel h2 { margin-bottom: 16px }
.q-card { background: #fafafa; border-radius: 8px; padding: 16px; margin-bottom: 10px }
.q-title { font-size: 14px; font-weight: 600; margin-bottom: 10px }
.tag { background: #eef2ff; color: #4f46e5; font-size: 11px; padding: 2px 6px; border-radius: 4px; margin-left: 6px; font-weight: 400 }
.options label { display: block; padding: 5px 6px; margin: 2px 0; border-radius: 5px; cursor: pointer; font-size: 13px; transition: .1s }
.options label:hover { background: #eef2ff }
.options label.chosen { background: #e0e7ff; color: #4f46e5; font-weight: 500 }
.options label.correctHighlight { background: #dcfce7; color: #38a169; font-weight: 500 }
.options label.wrongHighlight { background: #fef2f2; color: #e53e3e; font-weight: 500 }
.options input { margin-right: 6px }
.submit-bar { text-align: center; margin-top: 20px }
.btn-submit { background: #e53e3e; color: #fff; border: none; padding: 12px 48px; border-radius: 8px; font-size: 16px; cursor: pointer }
.btn-primary { background: #4f46e5; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }

/* 弹窗 */
.modal { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 100 }
.modal-box { background: #fff; border-radius: 12px; padding: 24px; width: 400px; max-height: 80vh; overflow-y: auto }
.modal-box h3 { margin-bottom: 14px }
.modal-btn { display: flex; gap: 12px; justify-content: flex-end; margin-top: 14px }
.modal-btn button { padding: 8px 20px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer }
.modal-btn .btn-primary { background: #4f46e5; color: #fff; border: none }
.del-msg { font-size: 14px; color: #555; margin-bottom: 8px }
.big-score { font-size: 24px; font-weight: bold; color: #4f46e5; margin: 16px 0 }

/* 答卷弹窗 */
.review-modal { width: 600px }
.review-score { font-size: 14px; color: #888; margin-bottom: 12px }
.rq-card { background: #fafafa; border-radius: 6px; padding: 10px 14px; margin-bottom: 8px; font-size: 13px }
.rq-card.rqCorrect { border-left: 4px solid #38a169 }
.rq-card.rqWrong { border-left: 4px solid #e53e3e }
.rq-title { font-weight: 500; margin-bottom: 4px }
.rq-opts span { display: inline-block; margin-right: 14px; font-size: 12px; color: #666 }
.rq-opts span.chosen { color: #e53e3e; font-weight: 600 }
.rq-opts span.correctHighlight { color: #38a169; font-weight: 600 }
.rq-result { margin-top: 4px; font-size: 12px }
.rg { color: #38a169; font-weight: 600 }
.rw { color: #e53e3e; font-weight: 600 }
.rc { color: #38a169; font-weight: 600 }

/* 练习模式 */
.practice-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 16px }
.ctrl-group { display: flex; align-items: center; gap: 6px; font-size: 14px }
.ctrl-group select { padding: 6px 10px; border: 1px solid #ddd; border-radius: 5px; font-size: 13px }
.ctrl-input { padding: 6px 10px; border: 1px solid #ddd; border-radius: 5px; font-size: 13px; width: 150px; outline: none }
.ctrl-input:focus { border-color: #4f46e5 }
.practice-progress { margin-bottom: 14px }
.progress-text { font-size: 14px; font-weight: 600; color: #4f46e5; margin-bottom: 6px }
.progress-bar-track { height: 8px; background: #e5e7eb; border-radius: 4px; overflow: hidden }
.progress-bar-fill { height: 100%; background: #4f46e5; border-radius: 4px; transition: width .3s }
.practice-feedback { margin-top: 12px; padding: 10px 14px; border-radius: 6px; font-size: 14px; font-weight: 500 }
.practice-feedback.correct { background: #dcfce7; color: #16a34a }
.practice-feedback.wrong { background: #fef2f2; color: #dc2626 }
.practice-next-bar { margin-top: 14px; text-align: center }
.practice-result { text-align: center; padding: 20px 0 }
.result-big { font-size: 42px; font-weight: 700; color: #4f46e5 }
.result-sub { font-size: 16px; color: #888; margin-top: 8px }

.btn-analyze-all {
  background: #10b981; color: #fff; border: none; padding: 5px 14px; border-radius: 5px;
  cursor: pointer; font-size: 12px; margin-left: auto; transition: .2s
}
.btn-analyze-all:hover { background: #059669 }
.btn-analyze-all:disabled { background: #a7f3d0; cursor: not-allowed }
.btn-analysis {
  background: none; color: #4f46e5; border: 1px solid #4f46e5; padding: 4px 12px;
  border-radius: 4px; cursor: pointer; font-size: 12px; transition: .2s; white-space: nowrap
}
.btn-analysis:hover { background: #eef2ff }
.btn-kill {
  background: none; color: #e53e3e; border: 1px solid #e53e3e; padding: 4px 10px;
  border-radius: 4px; cursor: pointer; font-size: 12px; transition: .2s; white-space: nowrap; margin-left: 6px
}
.btn-kill:hover { background: #fef2f2 }
.btn-kill.killed {
  color: #38a169; border-color: #38a169; background: #f0fdf4
}
.show-killed-label {
  font-size: 12px; color: #888; display: flex; align-items: center; gap: 4px; cursor: pointer; margin-left: auto; user-select: none
}
.show-killed-label input { width: auto; margin: 0 }
.row-killed td { color: #bbb !important; text-decoration: line-through }
.row-killed .correct-ans, .row-killed .wrong-ans { color: #bbb !important }
.row-killed .btn-analysis, .row-killed .btn-kill { opacity: 0.6 }
.row-killed .title-col-wrong { color: #bbb !important }

/* 知识图谱卡片 */
.wiki-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin-top: 12px }
.wiki-card {
  border-radius: 8px; padding: 16px; border-left: 4px solid #ddd; background: #fafafa; transition: .2s
}
.wiki-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.08) }
.wiki-level-1, .wiki-level-2 { border-left-color: #e53e3e; background: #fef5f5 }
.wiki-level-3 { border-left-color: #f59e0b; background: #fffbf0 }
.wiki-level-4, .wiki-level-5 { border-left-color: #38a169; background: #f0fdf4 }
.wiki-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px }
.wiki-name { font-size: 14px; font-weight: 600; color: #1f2937 }
.wiki-stars { font-size: 13px; color: #f59e0b; white-space: nowrap }
.wiki-stats { font-size: 12px; color: #888; margin-bottom: 8px }
.wiki-note { font-size: 12px; color: #444; line-height: 1.5; border-top: 1px dashed #ddd; padding-top: 8px }
.wiki-date { font-size: 11px; color: #aaa; margin-top: 8px }

/* 周报弹窗 */
.report-modal { width: 520px }
.report-summary { font-size: 15px; color: #333; line-height: 1.7; margin-bottom: 16px; padding: 12px; background: #f8fafc; border-radius: 6px }
.report-section { margin-bottom: 14px }
.report-section ul { margin: 4px 0 0 18px; font-size: 14px; color: #444 }
.report-section p { font-size: 14px; color: #444; line-height: 1.6; margin: 4px 0 0 0 }
.report-label { font-size: 12px; font-weight: 600; border-radius: 4px; padding: 2px 8px }
.r-green { color: #16a34a; background: #dcfce7 }
.r-red { color: #dc2626; background: #fef2f2 }
.r-blue { color: #4f46e5; background: #eef2ff }
.analysis-row td { padding: 0; border-bottom: 2px solid #e5e7eb }
.analysis-panel {
  background: #f0f9ff; border-left: 3px solid #4f46e5; border-radius: 0 6px 6px 0;
  padding: 12px 16px; margin: 4px 0
}
.analysis-loading { color: #888; font-size: 13px; padding: 8px 0 }
.analysis-section { margin-bottom: 10px }
.analysis-section:last-child { margin-bottom: 0 }
.analysis-label { font-size: 12px; font-weight: 600; color: #4f46e5; display: block; margin-bottom: 4px }
.analysis-section p { font-size: 13px; color: #333; line-height: 1.6; margin: 0 }
.kp-tags { display: flex; flex-wrap: wrap; gap: 6px }
.kp-tag { background: #e0e7ff; color: #4f46e5; font-size: 12px; padding: 2px 8px; border-radius: 12px }

/* 错题本表格新样式 */
.col-seq { width: 3%; text-align: center; font-size: 12px; color: #999 }
.col-cat { width: 6%; text-align: center; font-size: 12px; color: #666 }
.col-time { width: 8%; font-size: 12px; color: #888; white-space: nowrap }
.col-act { width: 12%; text-align: center; white-space: nowrap }
.title-col-wrong { cursor: pointer; font-size: 13px; line-height: 1.5; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.title-col-wrong:hover { color: #4f46e5 }
.opt-col { width: 26%; font-size: 12px; color: #666; vertical-align: top }
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

/* 错题详情弹窗 */
.detail-modal { width: 560px }
.detail-block { margin-bottom: 14px }
.detail-row { margin-bottom: 12px; font-size: 14px }
.detail-label { font-size: 12px; color: #888; margin-bottom: 4px }
.detail-text { font-size: 14px; color: #333; line-height: 1.6 }
</style>


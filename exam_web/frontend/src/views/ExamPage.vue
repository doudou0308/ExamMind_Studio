<!--
  ExamPage.vue —— 旧版考试页面（管理员自测用）
  管理员从题库中随机抽 5 题进行模拟考试，每题 20 分，满分 100 分
  新版学生考试在 StudentExam.vue 中
-->
<template>
  <div>
    <button class="btn-back" @click="$emit('back')">&larr; 返回</button>

    <!-- 开始界面：显示总题数和开始按钮 -->
    <div v-if="!exam.length" class="start-box">
      <p>共 {{ total }} 道题目</p>
      <button class="btn-start" @click="start">随机抽 5 题，开始考试</button>
    </div>

    <!-- 答题界面 -->
    <div v-else>
      <!-- 每题卡片：标题 + 选项 -->
      <div v-for="(q, i) in exam" :key="q.id" class="q-card">
        <div class="q-title">{{ i+1 }}. {{ q.title }} <span class="tag">{{ q.category }}</span></div>
        <!-- 四个选项 -->
        <div class="options">
          <label v-for="opt in ['A','B','C','D']" :key="opt"
            :class="{correct:submitted&&opt===q.answer, wrong:submitted&&answers[i]===opt&&opt!==q.answer}">
            <input type="radio" :name="'q'+i" :value="opt" v-model="answers[i]" :disabled="submitted" />
            {{ opt }}. {{ q['option'+opt] }}
          </label>
        </div>
        <!-- 提交后显示正确答案 -->
        <p v-if="submitted" class="correct-answer">正确答案：{{ q.answer }}</p>
      </div>

      <!-- 提交按钮（未交卷时） -->
      <div class="result-bar" v-if="!submitted">
        <button class="btn-submit" @click="submit">提交答案</button>
      </div>
      <!-- 得分结果（交卷后） -->
      <div class="result-bar" v-else>
        <span class="score">得分：{{ score }} / 100</span>
        <button class="btn-again" @click="start">再来一次</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listQuestions } from '../api'

defineEmits(['back'])

/** 题库总题数 */
const total = ref(0)
/** 当前抽取的 5 道考试题目 */
const exam = ref<any[]>([])
/** 每题的答案数组 */
const answers = ref<string[]>([])
/** 是否已交卷 */
const submitted = ref(false)
/** 得分（每题 20 分，满分 100） */
const score = ref(0)

/** 页面加载时获取题库总题数 */
onMounted(async () => {
  const r = await listQuestions()
  if (r.ok) total.value = r.total
})

/** 开始考试：从题库加载全部题目 */
async function start() {
  const r = await listQuestions(1, 1000)
  if (r.ok && r.data) {
    exam.value = r.data
    answers.value = new Array(r.data.length).fill('')
    submitted.value = false
    score.value = 0
  }
}

/** 提交答案：校验是否全部作答 → 计算得分 */
async function submit() {
  if (answers.value.some(a => !a)) { alert('请回答所有题目'); return }
  submitted.value = true
  score.value = exam.value.reduce((s:number, q:any, i:number) => s + (answers.value[i] === q.answer ? 20 : 0), 0)
}
</script>

<style scoped>
.btn-back { background:none; border:none; color:#4f46e5; font-size:14px; cursor:pointer; margin-bottom:16px }
.start-box { text-align:center; padding:60px 20px; background:#fff; border-radius:8px }
.start-box p { color:#666; margin-bottom:20px; font-size:16px }
.btn-start { background:#4f46e5; color:#fff; border:none; padding:12px 32px; border-radius:8px; font-size:16px; cursor:pointer }
.q-card { background:#fff; border-radius:8px; padding:20px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,.1) }
.q-title { font-size:15px; font-weight:600; margin-bottom:12px }
.tag { background:#eef2ff; color:#4f46e5; font-size:12px; padding:2px 8px; border-radius:4px; margin-left:8px; font-weight:400 }
.options label { display:block; padding:6px 8px; margin:4px 0; border-radius:6px; cursor:pointer; font-size:14px; transition:.15s }
.options label:hover { background:#f5f5f5 }
.options input { margin-right:8px }
.correct { background:#e6ffed!important; color:#2f855a }
.wrong { background:#ffe5e5!important; color:#c53030 }
.correct-answer { margin-top:8px; color:#666; font-size:13px }
.result-bar { text-align:center; margin-top:20px }
.btn-submit { background:#4f46e5; color:#fff; border:none; padding:10px 40px; border-radius:8px; font-size:16px; cursor:pointer }
.score { font-size:24px; font-weight:bold; color:#4f46e5 }
.btn-again { background:none; border:1px solid #4f46e5; color:#4f46e5; padding:8px 24px; border-radius:8px; margin-left:16px; cursor:pointer; font-size:14px }
</style>

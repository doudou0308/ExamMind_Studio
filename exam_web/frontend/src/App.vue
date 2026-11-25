<!--
  App.vue —— 应用根组件，控制整体页面结构：登录页 / 首次改密页 / 主功能页
  三种显示状态：
    1. 未登录 → 显示登录页
    2. 学生首次登录 → 显示强制修改密码页
    3. 正常登录 → 显示顶栏 + 功能页（根据角色展示不同菜单）
-->
<template>
  <div class="app">
    
    <!-- 未登录：显示登录界面 -->
    <LoginPage v-if="!store.loggedIn" />

    <!-- 学生首次登录：强制修改初始密码 -->
    <template v-else-if="needChangePassword">
      <!-- 顶部导航栏（精简版，仅显示标题和退出按钮） -->
      <header class="topbar">
        <h1>题库与考试系统</h1>
        <span class="spacer" />
        <span class="user-info">{{ store.user?.username }} (学生)</span>
        <button class="btn-logout" @click="store.logout()">退出</button>
      </header>
      <!-- 修改密码表单卡片 -->
      <main>
        <div class="pwd-card">
          <h2>首次登录 - 修改初始密码</h2>
          <p class="pwd-sub">为保障账号安全，请修改默认密码后再使用系统</p>
          <label>原密码 <input v-model="pwdForm.oldPassword" type="password" placeholder="请输入原密码" /></label>
          <label>新密码 <input v-model="pwdForm.newPassword" type="password" placeholder="不少于6位" /></label>
          <label>确认新密码 <input v-model="pwdForm.confirmPassword" type="password" placeholder="再次输入新密码" /></label>
          <p v-if="pwdMsg" :class="pwdOk ? 'pwd-ok' : 'pwd-err'">{{ pwdMsg }}</p>
          <button class="btn-submit" @click="doChangePassword">确认修改</button>
        </div>
      </main>
    </template>

    <!-- 已登录且无需改密：显示主功能界面 -->
    <template v-else>
      <!-- 顶部导航栏：根据角色显示不同菜单 -->
      <header class="topbar">
        <h1 @click="goHome">题库与考试系统</h1>
        <button v-if="store.user?.role==='admin'" :class="{active:page==='manage'}" @click="page='manage'">题库管理</button>
        <button v-if="store.user?.role==='admin'" :class="{active:page==='students'}" @click="page='students'">学生管理</button>
        <button v-if="store.user?.role==='admin'" :class="{active:page==='exams'}" @click="page='exams'">考试管理</button>
        <button v-if="store.user?.role==='admin'" :class="{active:page==='analyze'}" @click="page='analyze'">题库分析</button>
        <button v-if="store.user?.role==='student'" :class="{active:page==='exam'}" @click="page='exam'">开始考试</button>
        <span class="spacer" />
        <span class="user-info">{{ store.user?.username }} ({{ store.user?.role==='admin'?'管理员':'学生' }})</span>
        <button class="btn-logout" @click="store.logout()">退出</button>
      </header>
      <!-- 主内容区：根据 page 变量切换不同的功能组件 -->
      <main>
        <!-- 管理员 - 题库管理页 -->
        <QuestionManage v-if="page==='manage' && store.user?.role==='admin'" />
        <!-- 管理员 - 学生管理页 -->
        <StudentManage v-if="page==='students' && store.user?.role==='admin'" />
        <!-- 管理员 - 考试管理页 -->
        <ExamManage v-if="page==='exams' && store.user?.role==='admin'" />
        <QuestionAnalyze v-if="page==='analyze' && store.user?.role==='admin'" />
        <!-- 管理员 - 旧版考试页 -->
        <ExamPage v-if="page==='exam' && store.user?.role==='admin'" @back="page='exam'" />
        <!-- 学生 - 考试/练习页（核心功能） -->
        <StudentExam v-if="page==='exam' && store.user?.role==='student'" @back="page='exam'" />
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { store } from './store'
import { changePassword } from './api'
import LoginPage from './views/LoginPage.vue'
import QuestionManage from './views/QuestionManage.vue'
import StudentManage from './views/StudentManage.vue'
import ExamPage from './views/ExamPage.vue'
import ExamManage from './views/ExamManage.vue'
import QuestionAnalyze from './views/QuestionAnalyze.vue'
import StudentExam from './views/StudentExam.vue'

/** 当前显示的功能页标识：manage | students | exams | exam */
const page = ref('exam')

/** 计算属性：学生首次登录（isFirstLogin 为 true）时，需要进入改密码流程 */
const needChangePassword = computed(() =>
  store.loggedIn && store.user?.role === 'student' && store.user?.isFirstLogin === true
)

/** 修改密码表单数据 */
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdMsg = ref('')   // 提示信息
const pwdOk = ref(false) // 提示是否为成功消息

/** 监听登录状态变化：登录后自动切换对应角色的默认页面 */
watch(() => store.loggedIn, (val) => {
  if (val && store.user) {
    page.value = store.user.role === 'admin' ? 'manage' : 'exam'
  }
})

/** 点击标题返回首页（管理员→题库管理，学生→考试页） */
function goHome() {
  page.value = store.user?.role === 'admin' ? 'manage' : 'exam'
}

/** 执行修改密码：校验输入 → 调接口 → 更新本地状态 */
async function doChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword) { pwdMsg.value = '请输入原密码'; pwdOk.value = false; return }
  if (!newPassword || newPassword.length < 6) { pwdMsg.value = '新密码长度不能少于6位'; pwdOk.value = false; return }
  if (newPassword !== confirmPassword) { pwdMsg.value = '两次密码输入不一致'; pwdOk.value = false; return }
  if (newPassword === oldPassword) { pwdMsg.value = '新密码不能与原密码相同'; pwdOk.value = false; return }
  const res = await changePassword(oldPassword, newPassword)
  pwdMsg.value = res.msg
  pwdOk.value = res.ok
  if (res.ok) {
    if (store.user) store.user.isFirstLogin = false  // 改密成功后清除首次登录标记
  }
}
</script>

<style>
* { margin:0; padding:0; box-sizing:border-box }
body { font-family: 'Microsoft YaHei', sans-serif; background: #f0f2f5; color: #333 }
.topbar {
  background: #1a1a2e; color:#fff; padding:0 24px; height:56px;
  display:flex; align-items:center; gap:16px;
}
.topbar h1 { font-size:18px; cursor:pointer; margin-right:8px }
.topbar button {
  background:none; border:none; color:#aaa; font-size:14px; cursor:pointer;
  padding:8px 16px; border-radius:6px; transition:.2s;
}
.topbar button:hover, .topbar button.active { color:#fff; background:rgba(255,255,255,.15) }
.spacer { flex:1 }
.user-info { font-size:13px; color:#aaa }
.btn-logout { color:#e53e3e!important }
main { max-width:900px; margin:24px auto; padding:0 16px }

.pwd-card { background:#fff; border-radius:12px; padding:32px 40px; box-shadow:0 2px 8px rgba(0,0,0,.08); max-width:420px; margin:60px auto }
.pwd-card h2 { font-size:20px; margin-bottom:8px }
.pwd-sub { color:#888; font-size:13px; margin-bottom:20px }
.pwd-card label { display:block; margin-bottom:12px; font-size:14px }
.pwd-card input { width:100%; padding:10px; border:1px solid #ddd; border-radius:6px; margin-top:4px; font-size:14px; outline:none }
.pwd-card input:focus { border-color:#4f46e5 }
.pwd-ok { color:#38a169; font-size:13px; margin-bottom:8px }
.pwd-err { color:#e53e3e; font-size:13px; margin-bottom:8px }
.btn-submit { width:100%; padding:12px; background:#4f46e5; color:#fff; border:none; border-radius:8px; font-size:16px; cursor:pointer; margin-top:4px }
.btn-submit:hover { opacity:.9 }
</style>

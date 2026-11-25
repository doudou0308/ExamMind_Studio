<!--
  LoginPage.vue —— 登录页面
  两种角色登录方式：
    - 管理员：直接输入用户名 + 密码
    - 学生：从下拉列表选择自己的名字，再输入密码
  登录成功后调用 store.login() 写入全局状态
-->
<template>
  <div class="login-wrap">
    <div class="login-card">
      <h2>题库与考试系统</h2>
      <p class="sub">请登录</p>

      <!-- 角色选择下拉框 -->
      <label class="role-label">角色</label>
      <select v-model="role" class="role-select">
        <option value="admin">系统管理员</option>
        <option value="student">学生</option>
      </select>

      <!-- 学生角色：显示学生姓名下拉列表 -->
      <template v-if="role === 'student'">
        <label class="role-label">学生姓名</label>
        <select v-model="selectedStudentId" class="role-select">
          <option value="" disabled>请选择学生</option>
          <option v-for="s in studentList" :key="s.id" :value="s.id">{{ s.username }}</option>
        </select>
        <p v-if="studentError" class="stu-err">{{ studentError }}</p>
      </template>
      <!-- 管理员角色：显示用户名输入框 -->
      <template v-else>
        <input v-model="username" placeholder="用户名" @keyup.enter="doLogin" />
      </template>

      <!-- 密码输入框（两种角色共用） -->
      <input v-model="password" type="password" placeholder="密码" @keyup.enter="doLogin" />
      <!-- 错误/提示信息 -->
      <p v-if="msg" class="msg">{{ msg }}</p>
      <!-- 登录按钮 -->
      <button class="btn-login" @click="doLogin">登 录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { login, getStudentNames } from '../api'
import { store } from '../store'

/** 当前选择的角色：admin 或 student */
const role = ref('admin')
/** 管理员直接输入的用户名 */
const username = ref('')
/** 密码 */
const password = ref('')
/** 登录错误提示 */
const msg = ref('')
/** 学生下拉选中的学生ID */
const selectedStudentId = ref<number | ''>('')
/** 学生名称列表（从接口获取） */
const studentList = ref<{ id: number; username: string }[]>([])
/** 加载学生列表失败时的错误提示 */
const studentError = ref('')

/** 页面加载时获取所有学生名称，填充下拉列表 */
onMounted(async () => {
  try {
    const res = await getStudentNames()
    if (res.ok) {
      studentList.value = res.data
    } else {
      studentError.value = '加载学生列表失败，请刷新重试'
    }
  } catch {
    studentError.value = '加载学生列表异常，请刷新重试'
  }
})

/** 执行登录：学生角色从下拉列表获取用户名，管理员直接用输入框的用户名 */
async function doLogin() {
  if (role.value === 'student') {
    if (!selectedStudentId.value) 
    { 
      msg.value = '请选择学生'; return 
    }
    const student = studentList.value.find(s => s.id === selectedStudentId.value)
    if (!student) 
    { 
      msg.value = '未找到该学生'; return 
    }
    username.value = student.username
  }
  if (!username.value || !password.value) 
  { 
    msg.value = '请输入用户名和密码'; return 
  }
  const res = await login(username.value, password.value)
  if (res.ok) 
  {
    store.login(res.user, res.user.token) // 登录成功：写入全局状态，触发 App.vue 切换页面
  } else 
  {
    msg.value = res.msg || '登录失败'
  }
}
</script>

<style scoped>
.login-wrap { display:flex; align-items:center; justify-content:center; min-height:100vh; background:linear-gradient(135deg,#667eea,#764ba2) }
.login-card { background:#fff; padding:40px; border-radius:12px; width:360px; text-align:center; box-shadow:0 10px 40px rgba(0,0,0,.2) }
.login-card h2 { font-size:22px; margin-bottom:4px }
.sub { color:#888; margin-bottom:20px; font-size:14px }
.role-label { display:block; text-align:left; font-size:13px; color:#666; margin-bottom:4px; margin-top:2px }
.role-select { width:100%; padding:10px 12px; border:1px solid #e0e0e0; border-radius:8px; font-size:14px; margin-bottom:12px; outline:none; background:#fff; appearance:none }
.role-select:focus { border-color:#667eea }
input { width:100%; padding:12px; border:1px solid #e0e0e0; border-radius:8px; font-size:14px; margin-bottom:12px; outline:none }
input:focus { border-color:#667eea }
.msg { color:#e53e3e; font-size:13px; margin-bottom:8px }
.stu-err { color:#e53e3e; font-size:12px; margin-bottom:8px; text-align:left }
.btn-login { width:100%; padding:12px; background:#4f46e5; color:#fff; border:none; border-radius:8px; font-size:16px; cursor:pointer; margin-top:4px }
.btn-login:hover { opacity:.9 }
</style>
